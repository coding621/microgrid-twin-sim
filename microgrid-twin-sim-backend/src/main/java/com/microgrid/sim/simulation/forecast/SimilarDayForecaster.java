package com.microgrid.sim.simulation.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * 相似日预测器 — 基于 k-最近邻的时序预测。
 *
 * <p>算法：
 * <ol>
 *   <li>将历史数据按天切分（每天 24 个采样点）</li>
 *   <li>以最近一天为查询基准，找出 k 个最相似的历史天（MSE 距离）</li>
 *   <li>按相似度加权平均相似日的次日曲线</li>
 *   <li>用最近一天的总量比例缩放以捕捉趋势</li>
 * </ol>
 *
 * <p>前提条件：至少 3 天的历史数据。
 *
 * @author Coding
 */
public final class SimilarDayForecaster implements Forecaster {

    private static final Logger log = LoggerFactory.getLogger(SimilarDayForecaster.class);

    private final int H_pred;
    private final int k; // 相似天数

    private double[] cachedLoad;
    private double[] cachedPv;
    private double[] cachedTemp;
    private double lastLoadValue;
    private double lastPvValue;

    public SimilarDayForecaster(int horizon) {
        this(horizon, 5);
    }

    public SimilarDayForecaster(int horizon, int k) {
        this.H_pred = horizon;
        this.k = k;
    }

    @Override
    public String getName() {
        return "similar_day";
    }

    @Override
    public void update(double[] load, double[] pv, double[] temp) {
        int n = load.length;
        if (n < 48) {
            log.warn("相似日预测器: 数据点不足 {}，需要至少48点(2天)", n);
        }
        this.cachedLoad = Arrays.copyOf(load, n);
        this.cachedPv = Arrays.copyOf(pv, n);
        this.cachedTemp = Arrays.copyOf(temp, n);
        this.lastLoadValue = load[n - 1];
        this.lastPvValue = pv[n - 1];
    }

    @Override
    public double[][] predictLoad() {
        if (cachedLoad == null) {
            return createFallback(lastLoadValue);
        }
        return predict(cachedLoad, "load");
    }

    @Override
    public double[][] predictPv() {
        if (cachedPv == null) {
            return createFallback(lastPvValue);
        }
        return predict(cachedPv, "pv");
    }

    /**
     * 相似日预测核心算法。
     */
    private double[][] predict(double[] history, String label) {
        int n = history.length;
        int dayPoints = 24;

        if (n < dayPoints * 2) {
            log.debug("{}: 历史不足2天（{}点），回退最近值", label, n);
            return createFallback(history[n - 1]);
        }

        // 按天切分
        int nDays = n / dayPoints;
        int start = n - nDays * dayPoints;
        double[][] daily = new double[nDays][dayPoints];
        for (int d = 0; d < nDays; d++) {
            System.arraycopy(history, start + d * dayPoints, daily[d], 0, dayPoints);
        }

        if (nDays < 2) {
            return createFallback(history[n - 1]);
        }

        // 最后一天作为查询基准
        double[] lastDay = daily[nDays - 1];
        double lastDayMean = mean(lastDay);

        // 计算历史上每一天与最后一天的 MSE 距离（排除最后一天）
        int[] indices = new int[nDays - 1];
        double[] distances = new double[nDays - 1];
        for (int d = 0; d < nDays - 1; d++) {
            indices[d] = d;
            distances[d] = mse(daily[d], lastDay);
        }

        // 按距离排序，取 top-k
        sortByDistance(indices, distances);
        int kActual = Math.min(k, nDays - 1);

        // 加权平均：距离越小权重越大
        double[] weights = new double[kActual];
        double weightSum = 0;
        for (int i = 0; i < kActual; i++) {
            weights[i] = 1.0 / (distances[i] + 1e-6);
            weightSum += weights[i];
        }
        for (int i = 0; i < kActual; i++) {
            weights[i] /= weightSum;
        }

        // 加权平均相似日的次日曲线（取相似日的下一天）
        double[] q50 = new double[dayPoints];
        int usedCount = 0;
        for (int i = 0; i < kActual; i++) {
            int d = indices[i];
            if (d + 1 < nDays) {
                // 相似日的下一天作为预测模板
                double[] nextDay = daily[d + 1];
                for (int p = 0; p < dayPoints; p++) {
                    q50[p] += weights[i] * nextDay[p];
                }
                usedCount++;
            }
        }

        // 如果没有任何相似日有次日，回退到统计方法
        if (usedCount == 0) {
            return createFallback(history[n - 1]);
        }
        // 归一化权重
        if (usedCount < kActual) {
            double usedSum = 0;
            for (int i = 0; i < kActual; i++) {
                if (indices[i] + 1 < nDays) {
                    usedSum += weights[i];
                }
            }
            for (int p = 0; p < dayPoints; p++) {
                q50[p] /= usedSum;
            }
        }

        // 用最后一天的均值做趋势缩放
        double predMean = mean(q50);
        if (predMean > 0 && lastDayMean > 0) {
            double scale = lastDayMean / predMean;
            for (int p = 0; p < dayPoints; p++) {
                q50[p] *= scale;
            }
        }

        // 基于相似日间的方差估计不确定性
        double[] residuals = new double[dayPoints];
        for (int p = 0; p < dayPoints; p++) {
            double var = 0;
            int cnt = 0;
            for (int i = 0; i < kActual; i++) {
                int d = indices[i];
                if (d + 1 < nDays && d > 0) {
                    double diff = daily[d + 1][p] - daily[d][p];
                    var += diff * diff;
                    cnt++;
                }
            }
            residuals[p] = cnt > 0 ? Math.sqrt(var / cnt) : q50[p] * 0.1;
        }

        // 扩展到 H_pred
        double[] q05 = new double[H_pred];
        double[] q95 = new double[H_pred];
        double[] q50Ext = new double[H_pred];
        for (int h = 0; h < H_pred; h++) {
            int slot = h % dayPoints;
            q50Ext[h] = Math.max(0, q50[slot]);
            double noise = Math.min(residuals[slot], q50Ext[h] * 0.3);
            q05[h] = Math.max(0, q50Ext[h] - 1.645 * noise);
            q95[h] = Math.max(0, q50Ext[h] + 1.645 * noise);
        }

        log.debug("{} 相似日预测: {}天, k={}, 使用{}个有效相似日",
                label, nDays, kActual, usedCount);
        return new double[][]{q05, q50Ext, q95};
    }

    private double mse(double[] a, double[] b) {
        double sum = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return n > 0 ? sum / n : Double.MAX_VALUE;
    }

    private double mean(double[] a) {
        return Arrays.stream(a).average().orElse(0);
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
                double tmpD = distances[i];
                distances[i] = distances[minIdx];
                distances[minIdx] = tmpD;
                int tmpI = indices[i];
                indices[i] = indices[minIdx];
                indices[minIdx] = tmpI;
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