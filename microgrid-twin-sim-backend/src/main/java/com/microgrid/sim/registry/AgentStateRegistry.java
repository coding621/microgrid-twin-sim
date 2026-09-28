package com.microgrid.sim.registry;

import com.microgrid.sim.ws.dto.TickDataMessage;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * 智能体状态注册中心 - 线程安全地维护所有仿真实体的实时状态快照。
 *
 * <p>提供以下能力：
 * <ul>
 *   <li>各实体状态的读写</li>
 *   <li>聚合指标计算（总需求、总发电、CNP协商次数）</li>
 *   <li>预测数据及 fan chart 的存储</li>
 *   <li>RMSE 误差累积器</li>
 * </ul>
 *
 * @author Coding
 */
@Service
public class AgentStateRegistry {

    /** 智能体状态映射，key=智能体名称 */
    private final ConcurrentMap<String, TickDataMessage.AgentState> states = new ConcurrentHashMap<>();

    /**
     * 更新指定智能体的状态快照
     *
     * @param agentName 智能体名称
     * @param state     状态数据
     */
    public void update(String agentName, TickDataMessage.AgentState state) {
        states.put(agentName, state);
    }

    /**
     * 获取所有智能体的状态快照
     *
     * @return 状态映射（不可修改视图）
     */
    public Map<String, TickDataMessage.AgentState> all() {
        return states;
    }

    /* ==================== 预测相关字段 ==================== */

    /** 最新负载预测序列 */
    private volatile double[] latestForecastLoad = new double[0];
    /** 最新光伏预测序列 */
    private volatile double[] latestForecastPv = new double[0];
    /** fan chart: 光伏下限 */
    @Getter
    private volatile double[] fanLoPv = new double[0];
    /** fan chart: 光伏上限 */
    @Getter
    private volatile double[] fanHiPv = new double[0];
    /** fan chart: 负载下限 */
    @Getter
    private volatile double[] fanLoLoad = new double[0];
    /** fan chart: 负载上限 */
    @Getter
    private volatile double[] fanHiLoad = new double[0];
    /** 当前预测负载 (kW) */
    private volatile double predictedLoadKw = Double.NaN;
    /** 当前预测光伏 (kW) */
    private volatile double predictedPvKw = Double.NaN;
    /** 当前实际光伏总发电量 (kW) */
    @Getter
    @Setter
    private volatile double pvProduction = Double.NaN;

    /** 负载预测 RMSE 误差累积器 */
    private final DoubleAdder squaredErrorLoad = new DoubleAdder();
    /** 光伏预测 RMSE 误差累积器 */
    private final DoubleAdder squaredErrorPv = new DoubleAdder();
    /** RMSE 误差样本计数 */
    private final LongAdder errorSamples = new LongAdder();

    /* ==================== 聚合指标方法 ==================== */

    /**
     * 计算所有智能体的总电力需求
     *
     * @return 总需求 (kW)
     */
    public double getTotalEnergyDemand() {
        return states.values().stream()
                .mapToDouble(TickDataMessage.AgentState::getDemand)
                .sum();
    }

    /**
     * 计算所有智能体的总绿色能源发电量
     *
     * @return 总发电量 (kW)
     */
    public double getTotalGreenEnergyGeneration() {
        return states.values().stream()
                .mapToDouble(TickDataMessage.AgentState::getProduction)
                .sum();
    }

    /**
     * 计算所有智能体的 CNP 协商总次数
     *
     * @return 协商总次数
     */
    public double getTotalCnpNegotiations() {
        return states.values().stream()
                .mapToDouble(TickDataMessage.AgentState::getCnpNegotiations)
                .sum();
    }

    /* ==================== 预测数据存取 ==================== */

    /**
     * 设置当前预测值
     *
     * @param loadKw 负载预测 (kW)
     * @param pvKw   光伏预测 (kW)
     */
    public void setForecast(double loadKw, double pvKw) {
        this.predictedLoadKw = loadKw;
        this.predictedPvKw = pvKw;
    }

    /** @return 负载预测值 */
    public double getPredLoad() {
        return predictedLoadKw;
    }

    /** @return 光伏预测值 */
    public double getPredPv() {
        return predictedPvKw;
    }

    /** @return 负载预测序列 */
    public double[] getForecastLoad() {
        return latestForecastLoad;
    }

    /** @return 光伏预测序列 */
    public double[] getForecastPv() {
        return latestForecastPv;
    }

    /**
     * 更新预测序列
     *
     * @param fLoad 负载预测序列
     * @param fPv   光伏预测序列
     */
    public void updateForecast(double[] fLoad, double[] fPv) {
        this.latestForecastLoad = fLoad.clone();
        this.latestForecastPv = fPv.clone();
    }

    /* ==================== 误差统计方法 ==================== */

    /**
     * 添加误差样本（用于 RMSE 计算）
     *
     * @param errLoadKw 负载预测误差 (kW)
     * @param errPvKw   光伏预测误差 (kW)
     */
    public void addErrorSample(double errLoadKw, double errPvKw) {
        squaredErrorLoad.add(errLoadKw * errLoadKw);
        squaredErrorPv.add(errPvKw * errPvKw);
        errorSamples.increment();
    }

    /** 重置误差累积器 */
    public void resetErrorAccumulators() {
        squaredErrorLoad.reset();
        squaredErrorPv.reset();
        errorSamples.reset();
    }

    /**
     * 计算负载预测的当前 RMSE
     *
     * @return RMSE (kW)
     */
    public double currentRmseLoad() {
        return Math.sqrt(squaredErrorLoad.sum() / Math.max(1, errorSamples.sum()));
    }

    /**
     * 计算光伏预测的当前 RMSE
     *
     * @return RMSE (kW)
     */
    public double currentRmsePv() {
        return Math.sqrt(squaredErrorPv.sum() / Math.max(1, errorSamples.sum()));
    }

    /* ==================== Fan Chart 设置 ==================== */

    /**
     * 设置 fan chart 数据（预测区间）
     *
     * @param loLoad 负载下限 (q05)
     * @param hiLoad 负载上限 (q95)
     * @param loPv   光伏下限 (q05)
     * @param hiPv   光伏上限 (q95)
     */
    public void setFanChart(double[] loLoad, double[] hiLoad, double[] loPv, double[] hiPv) {
        fanLoLoad = loLoad;
        fanHiLoad = hiLoad;
        fanLoPv = loPv;
        fanHiPv = hiPv;
    }
}