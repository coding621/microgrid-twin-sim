package com.microgrid.sim.simulation.forecast;

/**
 * 预测评估指标 — 模型性能度量的核心数据结构。
 *
 * <p>支持 MAE、RMSE、MAPE、R² 四种核心指标，用于：
 * <ul>
 *   <li>训练阶段评估模型质量</li>
 *   <li>运行时概念漂移检测</li>
 *   <li>模型版本对比和选优</li>
 * </ul>
 *
 * @author Coding
 */
public class ForecastMetrics {

    /** 平均绝对误差 (Mean Absolute Error)，与预测目标同单位 */
    public final double mae;
    /** 均方根误差 (Root Mean Squared Error)，对大误差更敏感 */
    public final double rmse;
    /** 平均绝对百分比误差 (Mean Absolute Percentage Error)，无量纲百分比值 */
    public final double mape;
    /** 决定系数 (R²)，范围 (-∞, 1]，1 为完美预测，0 为均值预测，负值比均值还差 */
    public final double r2;
    /** 评估样本数 */
    public final int sampleCount;

    public ForecastMetrics(double mae, double rmse, double mape, double r2, int sampleCount) {
        this.mae = mae;
        this.rmse = rmse;
        this.mape = mape;
        this.r2 = r2;
        this.sampleCount = sampleCount;
    }

    /**
     * 从预测值和实际值批量计算所有评估指标。
     *
     * @param actual    实际观测值
     * @param predicted 模型预测值（通常用 q50 中位数预测）
     * @return 完整的评估指标对象
     */
    public static ForecastMetrics calculate(double[] actual, double[] predicted) {
        int n = Math.min(actual.length, predicted.length);
        if (n == 0) {
            return new ForecastMetrics(0, 0, 0, 0, 0);
        }

        double sumAbsErr = 0.0, sumSqErr = 0.0, sumAbsPct = 0.0;
        double sumActual = 0.0;

        for (int i = 0; i < n; i++) {
            double err = actual[i] - predicted[i];
            sumAbsErr += Math.abs(err);
            sumSqErr += err * err;
            sumActual += actual[i];
            // MAPE：避免除零，实际值过小时不计入百分比误差
            if (Math.abs(actual[i]) > 1e-6) {
                sumAbsPct += Math.abs(err / actual[i]);
            }
        }

        double mae = sumAbsErr / n;
        double rmse = Math.sqrt(sumSqErr / n);
        double mape = (sumAbsPct / n) * 100.0;

        // R² = 1 - SS_res / SS_tot
        double meanActual = sumActual / n;
        double ssTot = 0.0;
        for (int i = 0; i < n; i++) {
            double diff = actual[i] - meanActual;
            ssTot += diff * diff;
        }
        double r2 = ssTot > 1e-10 ? 1.0 - sumSqErr / ssTot : 0.0;

        return new ForecastMetrics(mae, rmse, mape, r2, n);
    }

    /**
     * 判断模型是否健康可用（R² > 0 且 MAPE < 50%）。
     *
     * @return true 表示模型可用
     */
    public boolean isHealthy() {
        return r2 > 0.0 && mape < 50.0;
    }

    @Override
    public String toString() {
        return String.format(
                "MAE=%.4f | RMSE=%.4f | MAPE=%.2f%% | R²=%.1f%% | N=%d",
                mae, rmse, mape, r2 * 100, sampleCount);
    }
}