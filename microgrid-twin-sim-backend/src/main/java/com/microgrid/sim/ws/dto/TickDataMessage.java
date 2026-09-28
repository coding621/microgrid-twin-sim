package com.microgrid.sim.ws.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Tick 数据消息 DTO - 每个 tick 通过 WebSocket 发送到前端的实时状态数据。
 *
 * <p>包含所有智能体的当前状态（需求、产量、SoC、故障状态等）、预测数据、
 * 预测误差以及 fan chart 区间数据。
 *
 * @author Coding
 */
@Getter
@Setter
public class TickDataMessage {
    /** 当前 tick 序号 */
    private long tickNumber;
    /** 所有智能体的状态快照，key=智能体名称，value=状态对象 */
    private Map<String, AgentState> agentStates;

    /** 负载预测中位数 (q50, kW) */
    private double predictedLoadKw;
    /** 光伏预测中位数 (q50, kW) */
    private double predictedPvKw;
    /** 负载预测误差增量 (kW) */
    private double errorLoadKw;
    /** 光伏预测误差增量 (kW) */
    private double errorPvKw;
    /** 负载 fan chart 下限 (q05) */
    private double[] fanLoLoad;
    /** 负载 fan chart 上限 (q95) */
    private double[] fanHiLoad;
    /** 光伏 fan chart 下限 (q05) */
    private double[] fanLoPv;
    /** 光伏 fan chart 上限 (q95) */
    private double[] fanHiPv;

    /**
     * 智能体状态内部类 - 描述单个智能体在某一时刻的状态快照。
     */
    @Getter
    @Setter
    public static class AgentState {
        /** CNP 协商次数 */
        private double cnpNegotiations;
        /** 当前电力需求 (kW) */
        private double demand;
        /** 当前发电量 (kW) */
        private double production;
        /** 储能状态 (SoC, kWh) */
        private double stateOfCharge;
        /** 是否发生故障 */
        private boolean broken;
    }
}