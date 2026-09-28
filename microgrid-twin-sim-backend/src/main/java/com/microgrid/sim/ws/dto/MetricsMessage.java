package com.microgrid.sim.ws.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 指标消息 DTO - 通过 WebSocket 发送到前端的聚合统计指标。
 *
 * <p>每隔 metricsPerNTicks 个 tick 发送一次，包含累计产量、消耗、绿色能源比率、
 * 预测误差等统计数据。
 *
 * @author Coding
 */
@Getter
@Setter
public class MetricsMessage {
    /** 当前 tick 序号 */
    private long tickNumber;
    /** 累计总发电量 (kWh) */
    private double totalProduced;
    /** 累计总耗电量 (kWh) */
    private double totalConsumed;
    /** CNP 协商总次数 */
    private double cnpNegotiations;
    /** 最近 N 个 tick 的总发电量 */
    private double totalProducedPerNTicks;
    /** 最近 N 个 tick 的总耗电量 */
    private double totalDemandPerNTicks;
    /** 绿色能源占比 (百分比) */
    private double greenEnergyRatioPct;
    /** 负载预测的 RMSE (kW) */
    private double rmseLoadKw;
    /** 光伏预测的 RMSE (kW) */
    private double rmsePvKw;
    /** 负载预测序列 */
    private double[] forecastLoadKw;
    /** 光伏预测序列 */
    private double[] forecastPvKw;
}