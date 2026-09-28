package com.microgrid.sim.simulation.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * 分位数树场景生成器 - 基于 3 分支分位数树生成场景。
 *
 * <p>生成三种场景：
 * <ul>
 *   <li>悲观场景 (q05)：负载高、光伏低，概率 15%</li>
 *   <li>中位场景 (q50)：负载中位、光伏中位，概率 70%</li>
 *   <li>乐观场景 (q95)：负载低、光伏高，概率 15%</li>
 * </ul>
 *
 * @author Coding
 */
public final class QuantileTreeGenerator implements ScenarioGenerator {

    /** 预测视野长度 */
    private final int H;

    /**
     * 构造函数
     *
     * @param horizon 预测视野（步数）
     */
    public QuantileTreeGenerator(int horizon) {
        this.H = horizon;
    }

    /**
     * 从分位数预测生成 3 个场景
     *
     * @param loadQ 负载分位数 [q05,q50,q95][H]
     * @param pvQ   光伏分位数 [q05,q50,q95][H]
     * @return 三个场景的列表
     */
    @Override
    public List<Scenario> generate(double[][] loadQ, double[][] pvQ) {
        double[] loL = loadQ[0], medL = loadQ[1], hiL = loadQ[2];
        double[] loP = pvQ[0], medP = pvQ[1], hiP = pvQ[2];

        List<Scenario> out = new ArrayList<>(3);
        out.add(new Scenario(loL, loP, 0.15));   // 悲观场景
        out.add(new Scenario(medL, medP, 0.70));  // 中位场景
        out.add(new Scenario(hiL, hiP, 0.15));   // 乐观场景
        return out;
    }
}