package com.microgrid.sim.simulation.scenario;

import java.util.List;

/**
 * 场景生成器接口 - 将概率预测转换为离散场景集合。
 *
 * <p>输入为负载和光伏的分位数预测数组 [q05, q50, q95][H_pred]，
 * 输出为离散场景列表，每个场景包含其实现概率。
 *
 * @author Coding
 */
public interface ScenarioGenerator {
    /**
     * 从分位数预测生成离散场景
     *
     * @param loadQ 负载分位数预测 [3][H_pred]，索引0/1/2分别对应 q05/q50/q95
     * @param pvQ   光伏分位数预测 [3][H_pred]
     * @return 离散场景列表
     */
    List<Scenario> generate(double[][] loadQ, double[][] pvQ);
}