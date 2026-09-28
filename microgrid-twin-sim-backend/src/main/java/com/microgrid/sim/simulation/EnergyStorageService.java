package com.microgrid.sim.simulation;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.service.EventControlService;
import com.microgrid.sim.service.LogAggregatorService;
import com.microgrid.sim.simulation.base.AbstractEnergyStorageEntity;
import com.microgrid.sim.simulation.models.Proposal;

/**
 * 储能电池服务
 *
 * <p>封装电池的充放电逻辑，并负责 CNP 协商中对充电/放电需求的提案生成。
 * 高电价时卖出、低电价时买入。
 *
 *
 * @author Coding
 */
public class EnergyStorageService extends AbstractEnergyStorageEntity {

    /** 当前 CNP 协商次数 */
    private volatile int cnpNegotiations = 0;

    /**
     * 构造函数
     *
     * @param name         实体名称
     * @param logger       日志服务
     * @param registry     状态注册中心
     * @param events       事件控制服务
     * @param capacity     电池容量 (kWh)
     * @param etaCharge    充电效率
     * @param etaDischarge 放电效率
     * @param cRate        C-rate (h⁻¹)
     * @param selfDischarge 自放电率
     * @param initialSoc   初始 SoC (kWh)
     */
    public EnergyStorageService(String name, LogAggregatorService logger,
                                AgentStateRegistry registry, EventControlService events,
                                double capacity, double etaCharge, double etaDischarge,
                                double cRate, double selfDischarge, double initialSoc) {
        super(name, logger, registry, events,
                capacity, etaCharge, etaDischarge, cRate, selfDischarge, initialSoc);
    }

    /**
     * 每个 tick 的行为：应用自放电
     */
    @Override
    public void onTick(long tick) {
        applySelfDischarge();
    }

    /**
     * 创建充电提案 (CNP respond) - 表示电池愿意吸收的电量
     *
     * @param price  当前电价
     * @param amount 需求充电量 (kWh)
     * @return 充电提案
     */
    public Proposal makeChargeProposal(double price, double amount) {
        Proposal p = new Proposal();
        p.setSender(name);
        // 电池愿意充电的量 = min(需求量, 可用空间, 最大功率)
        double willing = Math.min(amount, getAvailableCapacity());
        willing = Math.min(willing, maxChargePower);
        p.setAmount(willing);
        // 成本 = 电价 × 效率损失
        p.setCost(price * (1.0 - etaCharge));
        return p;
    }

    /**
     * 创建放电提案 (CNP respond) - 表示电池愿意释放的电量
     *
     * @param price  当前电价
     * @param amount 需求放电量 (kWh)
     * @return 放电提案
     */
    public Proposal makeDischargeProposal(double price, double amount) {
        Proposal p = new Proposal();
        p.setSender(name);
        // 电池愿意放电的量 = min(需求量, 可用能量, 最大功率)
        double willing = Math.min(amount, getAvailableEnergy());
        willing = Math.min(willing, maxDischargePower);
        p.setAmount(willing);
        // 收入 = 电价 × 放电效率损失
        p.setCost(price * (1.0 - etaDischarge));
        return p;
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