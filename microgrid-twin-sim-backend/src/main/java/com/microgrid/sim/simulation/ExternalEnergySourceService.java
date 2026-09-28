package com.microgrid.sim.simulation;

import com.microgrid.sim.simulation.models.Proposal;

/**
 * 外部能源服务
 *
 * <p>代表外部电网，提供无限容量的电力，但成本较高。
 * 当微电网内部供需不平衡时，通过 CNP 协商从外部电网补足短缺或出售盈余。
 *
 *
 * @author Coding
 */
public class ExternalEnergySourceService {

    /** 外部电网最大交互功率 (kW) */
    private final double maxPower;
    /** 外部电价 */
    private final double costPerKwh;
    /** CNP 协商次数 */
    private volatile int cnpNegotiations = 0;

    /**
     * 构造函数
     *
     * @param maxPower    最大交互功率 (kW)
     * @param costPerKwh  电价
     */
    public ExternalEnergySourceService(double maxPower, double costPerKwh) {
        this.maxPower = maxPower;
        this.costPerKwh = costPerKwh;
    }

    /**
     * 创建买入提案 - 表示外部电网可提供的电量
     *
     * @param amount 需求电量 (kWh)
     * @return 买入提案
     */
    public Proposal makeBuyProposal(double amount) {
        Proposal p = new Proposal();
        p.setSender("External");
        p.setAmount(Math.min(amount, maxPower));
        p.setCost(costPerKwh);  // 高成本
        return p;
    }

    /**
     * 创建卖出提案 - 表示外部电网愿吸收的盈余电量
     *
     * @param amount 盈余电量 (kWh)
     * @return 卖出提案
     */
    public Proposal makeSellProposal(double amount) {
        Proposal p = new Proposal();
        p.setSender("External");
        p.setAmount(Math.min(amount, maxPower));
        p.setCost(0.01);  // 收购盈余单价极低（鼓励内部平衡）
        return p;
    }

    /** @return 电价 */
    public double getCostPerKwh() {
        return costPerKwh;
    }

    /** @return 最大功率 (kW) */
    public double getMaxPower() {
        return maxPower;
    }

    /** @return CNP 协商次数 */
    public int getCnpNegotiations() {
        return cnpNegotiations;
    }

    /** 递增 CNP 协商计数 */
    public void incrementCnpNegotiations() {
        cnpNegotiations++;
    }
}