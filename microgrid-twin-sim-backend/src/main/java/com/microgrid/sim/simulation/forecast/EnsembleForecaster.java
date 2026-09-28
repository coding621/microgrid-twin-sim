package com.microgrid.sim.simulation.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * 集成预测器 — 组合多种时序方法并加权融合。
 *
 * <p>内置 4 种子方法：
 * <ol>
 *   <li>同时刻均值 — 历史上同一时刻的平均值</li>
 *   <li>相似日 — k=3 相似日加权平均</li>
 *   <li>线性趋势 — 日总量线性外推 + 日均曲线</li>
 *   <li>贝叶斯后验 — 每个时间槽的贝叶斯更新</li>
 * </ol>
 *
 * <p>权重策略：
 * <ul>
 *   <li>工作日: 同时刻均值(0.30) + 相似日(0.30) + 线性趋势(0.25) + 贝叶斯(0.15)</li>
 *   <li>周末:   同时刻均值(0.40) + 相似日(0.35) + 贝叶斯(0.25)</li>
 * </ul>
 *
 * <p>周末/工作日自动检测：最近一天的总量低于7天均值×0.75 则判定为周末。
 *
 * @author Coding
 */
public final class EnsembleForecaster implements Forecaster {

    private static final Logger log = LoggerFactory.getLogger(EnsembleForecaster.class);

    private final int H_pred;
    private static final int DAY_POINTS = 24;

    private double[] cachedLoad;
    private double[] cachedPv;
    private double[] cachedTemp;
    private double lastLoadValue;
    private double lastPvValue;

    public EnsembleForecaster(int horizon) {
        this.H_pred = horizon;
    }

    @Override
    public String getName() {
        return "ensemble";
    }

    @Override
    public void update(double[] load, double[] pv, double[] temp) {
        int n = load.length;
        if (n < DAY_POINTS * 2) {
            log.warn("集成预测器: 数据点不足 {}，需要至少 48 点(2天)", n);
        }
        this.cachedLoad = Arrays.copyOf(load, n);
        this.cachedPv = Arrays.copyOf(pv, n);
        this.cachedTemp = Arrays.copyOf(temp, n);
        this.lastLoadValue = load[n - 1];
        this.lastPvValue = pv[n - 1];
    }

    @Override
    public double[][] predictLoad() {
        if (cachedLoad == null || cachedLoad.length < DAY_POINTS * 2) {
            return createFallback(lastLoadValue);
        }
        return ensemble(cachedLoad, "load");
    }

    @Override
    public double[][] predictPv() {
        if (cachedPv == null || cachedPv.length < DAY_POINTS * 2) {
            return createFallback(lastPvValue);
        }
        return ensemble(cachedPv, "pv");
    }

    private double[][] ensemble(double[] history, String label) {
        int n = history.length;
        int nDays = n / DAY_POINTS;
        int start = n - nDays * DAY_POINTS;

        double[][] daily = new double[nDays][DAY_POINTS];
        for (int d = 0; d < nDays; d++) {
            System.arraycopy(history, start + d * DAY_POINTS, daily[d], 0, DAY_POINTS);
        }

        // 周末/工作日检测
        boolean isWeekend = detectWeekend(daily, nDays);

        // 运行各子方法
        double[][] results = new double[4][];
        int methodCount = 0;

        try {
            results[0] = predictMean(daily, nDays);
            methodCount++;
        } catch (Exception e) {
            results[0] = null;
        }
        try {
            results[1] = predictSimilarDay(daily, nDays);
            methodCount++;
        } catch (Exception e) {
            results[1] = null;
        }
        try {
            results[2] = predictLinearTrend(daily, nDays);
            methodCount++;
        } catch (Exception e) {
            results[2] = null;
        }
        try {
            results[3] = predictBayesian(daily, nDays);
            methodCount++;
        } catch (Exception e) {
            results[3] = null;
        }

        // 权重配置
        double weightMean, weightSimilar, weightLinear, weightBayesian;
        if (isWeekend) {
            weightMean = 0.40;
            weightSimilar = 0.35;
            weightLinear = 0.0;
            weightBayesian = 0.25;
        } else {
            weightMean = 0.30;
            weightSimilar = 0.30;
            weightLinear = 0.25;
            weightBayesian = 0.15;
        }

        // 加权融合
        double[] fused = new double[DAY_POINTS];
        double totalWeight = 0;

        if (results[0] != null) {
            addWeighted(fused, results[0], weightMean);
            totalWeight += weightMean;
        }
        if (results[1] != null) {
            addWeighted(fused, results[1], weightSimilar);
            totalWeight += weightSimilar;
        }
        if (results[2] != null) {
            addWeighted(fused, results[2], weightLinear);
            totalWeight += weightLinear;
        }
        if (results[3] != null) {
            addWeighted(fused, results[3], weightBayesian);
            totalWeight += weightBayesian;
        }

        if (totalWeight > 0) {
            for (int p = 0; p < DAY_POINTS; p++) {
                fused[p] /= totalWeight;
            }
        } else {
            double lastVal = history[n - 1];
            Arrays.fill(fused, lastVal);
        }

        // 基于子方法间的分歧估计不确定性
        double[] q05 = new double[H_pred];
        double[] q50 = new double[H_pred];
        double[] q95 = new double[H_pred];
        for (int h = 0; h < H_pred; h++) {
            int slot = h % DAY_POINTS;
            q50[h] = Math.max(0, fused[slot]);

            double divergence = computeDivergence(results, slot, q50[h]);
            q05[h] = Math.max(0, q50[h] - divergence);
            q95[h] = Math.max(0, q50[h] + divergence);
        }

        log.debug("{} 集成预测: {}天, {}, {}种方法有效",
                label, nDays, isWeekend ? "周末" : "工作日", methodCount);
        return new double[][]{q05, q50, q95};
    }

    // ==================== 子方法 ====================

    /**
     * 同时刻均值 — 历史上同一小时的平均值。
     */
    private double[] predictMean(double[][] daily, int nDays) {
        double[] result = new double[DAY_POINTS];
        for (int p = 0; p < DAY_POINTS; p++) {
            double sum = 0;
            for (int d = 0; d < nDays; d++) {
                sum += daily[d][p];
            }
            result[p] = sum / nDays;
        }
        return result;
    }

    /**
     * 相似日 — k=3，与最后一天的 MSE 距离越小权重越高。
     */
    private double[] predictSimilarDay(double[][] daily, int nDays) {
        int k = Math.min(3, nDays - 1);
        double[] lastDay = daily[nDays - 1];

        int[] indices = new int[nDays - 1];
        double[] distances = new double[nDays - 1];
        for (int d = 0; d < nDays - 1; d++) {
            indices[d] = d;
            double sumSq = 0;
            for (int p = 0; p < DAY_POINTS; p++) {
                double diff = daily[d][p] - lastDay[p];
                sumSq += diff * diff;
            }
            distances[d] = sumSq;
        }

        sortByDistance(indices, distances);

        double[] weights = new double[k];
        double weightSum = 0;
        for (int i = 0; i < k; i++) {
            weights[i] = 1.0 / (distances[i] + 1e-6);
            weightSum += weights[i];
        }
        if (weightSum == 0) {
            return lastDay;
        }
        for (int i = 0; i < k; i++) {
            weights[i] /= weightSum;
        }

        double[] result = new double[DAY_POINTS];
        for (int i = 0; i < k; i++) {
            int d = indices[i];
            double[] day = (d + 1 < nDays) ? daily[d + 1] : daily[d];
            for (int p = 0; p < DAY_POINTS; p++) {
                result[p] += weights[i] * day[p];
            }
        }
        return result;
    }

    /**
     * 线性趋势 — 日总量线性外推 + 日均曲线。
     */
    private double[] predictLinearTrend(double[][] daily, int nDays) {
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

        double[] profile = new double[DAY_POINTS];
        for (int p = 0; p < DAY_POINTS; p++) {
            double sum = 0;
            for (int d = 0; d < nDays; d++) {
                sum += daily[d][p];
            }
            profile[p] = sum / nDays;
        }
        double profileSum = Arrays.stream(profile).sum();

        double[] result = new double[DAY_POINTS];
        for (int p = 0; p < DAY_POINTS; p++) {
            result[p] = profileSum > 0
                    ? forecastTotal * profile[p] / profileSum
                    : forecastTotal / DAY_POINTS;
        }
        return result;
    }

    /**
     * 贝叶斯后验 — 每个时间槽独立做贝叶斯更新。
     */
    private double[] predictBayesian(double[][] daily, int nDays) {
        double[] result = new double[DAY_POINTS];
        for (int p = 0; p < DAY_POINTS; p++) {
            double[] slot = new double[nDays];
            for (int d = 0; d < nDays; d++) {
                slot[d] = daily[d][p];
            }

            double mean = Arrays.stream(slot).average().orElse(0);
            double var = 0;
            for (int d = 0; d < nDays; d++) {
                double diff = slot[d] - mean;
                var += diff * diff;
            }
            var = var / nDays + 1e-6;

            // 用最近 3 天作为似然
            int recentN = Math.min(3, nDays);
            double recentMean = 0;
            for (int d = nDays - recentN; d < nDays; d++) {
                recentMean += slot[d];
            }
            recentMean /= recentN;
            double recentVar = 0;
            for (int d = nDays - recentN; d < nDays; d++) {
                double diff = slot[d] - recentMean;
                recentVar += diff * diff;
            }
            recentVar = recentVar / recentN + 1e-6;

            double postVar = 1.0 / (1.0 / var + recentN / recentVar);
            double postMean = postVar * (mean / var + recentN * recentMean / recentVar);
            result[p] = Math.max(0, postMean);
        }
        return result;
    }

    // ==================== 辅助方法 ====================

    private void addWeighted(double[] target, double[] source, double weight) {
        for (int i = 0; i < target.length && i < source.length; i++) {
            target[i] += weight * source[i];
        }
    }

    /** 计算子方法间的分歧作为不确定性估计 */
    private double computeDivergence(double[][] results, int slot, double center) {
        double sumSq = 0;
        int count = 0;
        for (double[] r : results) {
            if (r != null && slot < r.length) {
                double diff = r[slot] - center;
                sumSq += diff * diff;
                count++;
            }
        }
        return count > 0 ? Math.sqrt(sumSq / count) * 1.645 : center * 0.1;
    }

    /** 周末/工作日检测：最近一天总量低于7天均值×0.75 → 周末 */
    private boolean detectWeekend(double[][] daily, int nDays) {
        if (nDays < 7) {
            return false;
        }
        int lookback = Math.min(7, nDays);
        double recentTotal = Arrays.stream(daily[nDays - 1]).sum();
        double avgTotal = 0;
        for (int d = nDays - lookback; d < nDays - 1; d++) {
            avgTotal += Arrays.stream(daily[d]).sum();
        }
        avgTotal /= (lookback - 1);
        return avgTotal > 0 && recentTotal < avgTotal * 0.75;
    }

    private void sortByDistance(int[] indices, double[] distances) {
        int n = Math.min(indices.length, distances.length);
        for (int i = 0; i < n - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < n; j++) {
                if (distances[j] < distances[minIdx]) {
                    minIdx = j;
                }
            }
            if (minIdx != i) {
                double td = distances[i];
                distances[i] = distances[minIdx];
                distances[minIdx] = td;
                int ti = indices[i];
                indices[i] = indices[minIdx];
                indices[minIdx] = ti;
            }
        }
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
    }

    @Override
    public boolean loadModels() {
        return false;
    }
}