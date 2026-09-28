package com.microgrid.sim.simulation.scenario;

/** 场景数据记录 - 表示未来 H_pred 小时的一种预测实现路径。
 * @author Coding*/
public record Scenario(double[] loadKw, double[] pvKw, double prob) {
    /** @return 预测视野长度 */
    public int horizon() {
        return loadKw.length;
    }
}