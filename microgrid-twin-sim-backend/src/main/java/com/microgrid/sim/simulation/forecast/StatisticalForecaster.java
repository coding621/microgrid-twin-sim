package com.microgrid.sim.simulation.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * 统计型预测器 — 纯时间序列分解，零外部依赖。
 *
 * <p>算法：
 * <ol>
 *   <li>从历史数据提取日均曲线模式</li>
 *   <li>从日总量序列估计线性趋势</li>
 *   <li>日均曲线 × 趋势投影 = 预测</li>
 *   <li>基于历史残差估计不确定性，输出分位数</li>
 * </ol>
 *
 * <p>特点：无需训练、无需模型文件、零冷启动成本、极低 CPU 开销。
 *
 * @author Coding
 */
public final class StatisticalForecaster implements Forecaster {

    private static final Logger log = LoggerFactory.getLogger(StatisticalForecaster.class);

    private final int H_pred;
    private double[] cachedLoad;
    private double[] cachedPv;
    private double[] cachedTemp;
    private double lastLoadValue;
    private double lastPvValue;

    public StatisticalForecaster(int horizon) {
        this.H_pred = horizon;
    }

    @Override
    public String getName() {
        return "statistical";
    }

    @Override
    public void update(double[] load, double[] pv, double[] temp) {
        int n = load.length;
        if (n < 2) {
            log.warn("统计预测器: 数据点不足 {}", n);
            return;
        }
        this.cachedLoad = Arrays.copyOf(load, n);
        this.cachedPv = Arrays.copyOf(pv, n);
        this.cachedTemp = Arrays.copyOf(temp, n);
        this.lastLoadValue = load[n - 1];
        this.lastPvValue = pv[n - 1];
        log.debug("统计预测器: 缓存 {} 个历史点", n);
    }

    @Override
    public double[][] predictLoad() {
        if (cachedLoad == null || cachedLoad.length < 2) {
            return createFallback(lastLoadValue);
        }
        return decompose(cachedLoad, "load");
    }

    @Override
    public double[][] predictPv() {
        if (cachedPv == null || cachedPv.length < 2) {
            return createFallback(lastPvValue);
        }
        return decompose(cachedPv, "pv");
    }

    /**
     * 时序分解预测 — 日均曲线 + 线性趋势 + 残差噪声。
     *
     * <p>假设数据点按小时采样，H_pred 步对应未来 H_pred 小时。
     * 日模式周期 = 24 点（小时级采样）。
     *
     * @param history 历史数据
     * @param label   数据标签（用于日志）
     * @return 分位数预测 [q05, q50, q95][H_pred]
     */
    private double[][] decompose(double[] history, String label) {
        int n = history.length;
        int dayPoints = 24; // 假设每天 24 个采样点（小时级）

        // 如果数据不足一天，退化为最近值预测
        if (n < dayPoints) {
            log.debug("{}: 历史数据不足一天 ({} 点)，使用最近值预测", label, n);
            return createFallback(history[n - 1]);
        }

        // 将数据组织为天 × 点
        int nDays = n / dayPoints;
        if (nDays < 2) {
            return createFallback(history[n - 1]);
        }

        double[][] daily = new double[nDays][dayPoints];
        int start = n - nDays * dayPoints; // 跳过不足一天的部分
        for (int d = 0; d < nDays; d++) {
            System.arraycopy(history, start + d * dayPoints, daily[d], 0, dayPoints);
        }

        // 1. 计算日均曲线
        double[] dailyProfile = new double[dayPoints];
        for (int p = 0; p < dayPoints; p++) {
            double sum = 0;
            for (int d = 0; d < nDays; d++) {
                sum += daily[d][p];
            }
            dailyProfile[p] = sum / nDays;
        }

        // 2. 日总量趋势（线性拟合）
        double[] dailyTotals = new double[nDays];
        for (int d = 0; d < nDays; d++) {
            dailyTotals[d] = Arrays.stream(daily[d]).sum();
        }

        double slope = 0, intercept = 0;
        if (nDays >= 2) {
            double meanX = (nDays - 1) / 2.0;
            double meanY = Arrays.stream(dailyTotals).average().orElse(0);
            double num = 0, den = 0;
            for (int i = 0; i < nDays; i++) {
                double dx = i - meanX;
                num += dx * (dailyTotals[i] - meanY);
                den += dx * dx;
            }
            slope = den > 0 ? num / den : 0;
            intercept = meanY - slope * meanX;
        } else {
            intercept = dailyTotals[0];
        }

        double forecastTotal = Math.max(0, intercept + slope * nDays);

        // 3. 残差噪声水平估计
        double noiseStd = 0;
        if (nDays >= 2) {
            double[] residuals = new double[nDays];
            for (int d = 0; d < nDays; d++) {
                double predicted = intercept + slope * d;
                residuals[d] = dailyTotals[d] - predicted;
            }
            double meanRes = Arrays.stream(residuals).average().orElse(0);
            double varRes = Arrays.stream(residuals).map(r -> (r - meanRes) * (r - meanRes)).average().orElse(0);
            noiseStd = Math.sqrt(varRes);
            noiseStd = Math.max(noiseStd, forecastTotal * 0.02); // 保底噪声
            noiseStd = Math.min(noiseStd, forecastTotal * 0.20); // 噪声上限
        } else {
            noiseStd = forecastTotal * 0.05;
        }

        // 4. 投影预测
        double profileSum = Arrays.stream(dailyProfile).sum();
        double[] q05 = new double[H_pred];
        double[] q50 = new double[H_pred];
        double[] q95 = new double[H_pred];

        for (int h = 0; h < H_pred; h++) {
            int slot = h % dayPoints;
            double base = profileSum > 0
                    ? forecastTotal * dailyProfile[slot] / profileSum
                    : forecastTotal / dayPoints;

            q50[h] = Math.max(0, base);
            // 分位数：±1.645σ 对应 90% 置信区间 (q05 ~ q95)
            double perSlotNoise = noiseStd / dayPoints;
            q05[h] = Math.max(0, q50[h] - 1.645 * perSlotNoise);
            q95[h] = Math.max(0, q50[h] + 1.645 * perSlotNoise);
        }

        log.debug("{} 统计预测: {}天, 趋势斜率={:.2f}, 预测总量={:.1f}, 噪声σ={:.2f}",
                label, nDays, slope, forecastTotal, noiseStd);
        return new double[][]{q05, q50, q95};
    }

    private double[][] createFallback(double lastValue) {
        double[][] result = new double[3][H_pred];
        for (int h = 0; h < H_pred; h++) {
            result[0][h] = Math.max(0, lastValue * 0.8);
            result[1][h] = Math.max(0, lastValue);
            result[2][h] = lastValue * 1.2;
        }
        return result;
    }

    @Override
    public ForecastMetrics getLoadMetrics() {
        return null;
    }

    @Override
    public ForecastMetrics getPvMetrics() {
        return null;
    }

    @Override
    public void setModelSavePath(String path) {
        // 统计方法无需持久化
    }

    @Override
    public boolean loadModels() {
        return false;
    }
}