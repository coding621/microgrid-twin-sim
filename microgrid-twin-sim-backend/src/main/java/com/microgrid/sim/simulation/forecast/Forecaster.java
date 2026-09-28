package com.microgrid.sim.simulation.forecast;

/**
 * 预测器统一接口 — 所有预测算法必须实现此接口。
 *
 * <p>职责：接收历史数据，产出分位数预测，支撑下游场景生成和线性规划调度。
 *
 * <p>输出规范：
 * <ul>
 *   <li>predictLoad() / predictPv() 返回 [q05, q50, q95][H_pred]</li>
 *   <li>q05 为悲观预测（下分位），q50 为中位预测，q95 为乐观预测（上分位）</li>
 *   <li>确定性算法可将点估计包装为 [0.8×pred, pred, 1.2×pred]</li>
 * </ul>
 *
 * @author Coding
 */
public interface Forecaster {

    /**
     * 使用最新历史数据更新/训练预测模型。
     * 对于离线训练的模型，此方法可以是空操作。
     *
     * @param load 历史负载数据（按时间从旧到新排列）
     * @param pv   历史光伏数据（按时间从旧到新排列）
     * @param temp 历史温度数据（按时间从旧到新排列）
     */
    void update(double[] load, double[] pv, double[] temp);

    /**
     * 预测未来 H_pred 步的负载值。
     *
     * @return 分位数预测 [q05, q50, q95][H_pred]
     */
    double[][] predictLoad();

    /**
     * 预测未来 H_pred 步的光伏值。
     *
     * @return 分位数预测 [q05, q50, q95][H_pred]
     */
    double[][] predictPv();

    /**
     * 最近一次负载预测的评估指标。
     *
     * @return 评估指标，未训练时返回 null
     */
    ForecastMetrics getLoadMetrics();

    /**
     * 最近一次光伏预测的评估指标。
     *
     * @return 评估指标，未训练时返回 null
     */
    ForecastMetrics getPvMetrics();

    /**
     * 设置模型持久化目录路径。
     *
     * @param path 持久化目录，为 null 或空字符串时不持久化
     */
    void setModelSavePath(String path);

    /**
     * 从磁盘加载已持久化的模型。
     *
     * @return true 表示加载成功，false 表示模型不存在或加载失败
     */
    boolean loadModels();

    /**
     * 算法名称标识。
     *
     * @return 如 "random_forest"、"statistical"、"similar_day"
     */
    String getName();
}