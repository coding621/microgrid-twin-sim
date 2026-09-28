package com.microgrid.sim.simulation.models;

import lombok.Data;

/**
 * 报价提案模型 - 表示一个智能体在CNP（合同网协议）中对短fall/盈余需求的响应报价。
 *
 * <p>该模型在聚合器处理短fall和盈余协商时使用，包含供给方信息、报价金额、成本和到达顺序。
 *
 * @author Coding
 */
@Data
public class Proposal {
    /** 供给方标识名称 */
    String sender;
    /** 可供应的电量 (kWh) */
    double amount;
    /** 供应成本（效率损失） */
    double cost;
    /** 提案到达的序号，用于打破评分平局 */
    int arrivalIndex;
    /** 最终被接受的供应量 (kWh) */
    double acceptedAmount;
}