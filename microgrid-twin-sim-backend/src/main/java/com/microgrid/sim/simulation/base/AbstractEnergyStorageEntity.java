package com.microgrid.sim.simulation.base;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.service.EventControlService;
import com.microgrid.sim.service.LogAggregatorService;

/**
 * 储能实体抽象基类 。
 *
 * <p>封装了储能系统的充放电逻辑和 SoC（荷电状态）管理：
 * <ul>
 *   <li>充电效率 ηC、放电效率 ηD</li>
 *   <li>充放电倍率 (C-rate) 限制</li>
 *   <li>自放电率建模</li>
 *   <li>SoC 上下限约束</li>
 * </ul>
 *
 * @author Coding
 */
public abstract class AbstractEnergyStorageEntity extends AbstractSimEntity {

    /** 电池容量 (kWh) */
    protected final double capacity;
    /** 充电效率 ηC (0-1) */
    protected final double etaCharge;
    /** 放电效率 ηD (0-1) */
    protected final double etaDischarge;
    /** 最大充放电功率 (kW) = C-rate × 容量 */
    protected final double maxPower;
    /** 最大充电功率 (kW) */
    protected double maxChargePower;
    /** 最大放电功率 (kW) */
    protected double maxDischargePower;
    /** 自放电率 (每小时比例) */
    protected final double selfDischarge;

    /** 当前荷电状态 SoC (kWh) */
    protected volatile double soc;
    /** 初始 SoC (kWh) */
    protected final double initialSoc;
    /** 当前累计购买成本 */
    protected double totalBuyCost = 0.0;
    /** 当前累计销售收入 */
    protected double totalSellRevenue = 0.0;

    /**
     * 构造函数
     *
     * @param name         实体名称
     * @param logger       日志服务
     * @param registry     状态注册中心
     * @param events       事件控制服务
     * @param capacity     电池容量 (kWh)
     * @param etaCharge    充电效率 (0-1)
     * @param etaDischarge 放电效率 (0-1)
     * @param cRate        充放电倍率 (h⁻¹)
     * @param selfDischarge 自放电率 (每小时比例)
     * @param initialSoc   初始 SoC (kWh)
     */
    public AbstractEnergyStorageEntity(String name, LogAggregatorService logger,
                                       AgentStateRegistry registry, EventControlService events,
                                       double capacity, double etaCharge, double etaDischarge,
                                       double cRate, double selfDischarge, double initialSoc) {
        super(name, logger, registry, events);
        this.capacity = capacity;
        this.etaCharge = etaCharge;
        this.etaDischarge = etaDischarge;
        this.maxPower = cRate * capacity;
        this.maxChargePower = cRate * capacity;
        this.maxDischargePower = cRate * capacity;
        this.selfDischarge = selfDischarge;
        this.initialSoc = initialSoc;
        this.soc = initialSoc;
    }

    /** @return 当前电量生产（充电为负消耗，发电量为0） */
    @Override
    public double getProduction() {
        return 0.0; // 储能不发电
    }

    /** @return 当前电力需求，正数=需要充电，负数=可放电 */
    @Override
    public double getDemand() {
        return 0.0;
    }

    /** @return 当前 SoC (kWh) */
    public double getSoc() {
        return soc;
    }

    /** @return 最大充电功率 (kW) */
    public double getMaxChargePower() {
        return maxChargePower;
    }

    /** @return 最大放电功率 (kW) */
    public double getMaxDischargePower() {
        return maxDischargePower;
    }

    /** @return 可用容量 */
    public double getAvailableCapacity() {
        return capacity - soc;
    }

    /** @return 可放电量 (kWh) */
    public double getAvailableEnergy() {
        return soc;
    }

    /**
     * 执行充电操作
     *
     * @param kWh 充电电量 (kWh)，限制在可用空间和最大功率之间
     * @return 实际充电量 (kWh)
     */
    public double charge(double kWh) {
        double energyToCharge = Math.min(kWh, getAvailableCapacity());
        energyToCharge = Math.min(energyToCharge, maxChargePower);
        double actualEnergy = energyToCharge * etaCharge;
        soc += actualEnergy;
        return energyToCharge;
    }

    /**
     * 执行放电操作
     *
     * @param kWh 需求放电量 (kWh)，限制在可用电量和最大功率之间
     * @return 实际放电量 (kWh)
     */
    public double discharge(double kWh) {
        double energyToDischarge = Math.min(kWh, soc);
        energyToDischarge = Math.min(energyToDischarge, maxDischargePower);
        double actualEnergy = energyToDischarge / etaDischarge;
        soc -= actualEnergy;
        if (soc < 0) {
            soc = 0;
        }
        return energyToDischarge;
    }

    /**
     * 按固定功率放电
     *
     * @param kw 放电功率 (kW)
     * @return 实际放电量 (kWh)
     */
    public double dischargeAtRate(double kw) {
        if (kw <= 0) {
            return 0;
        }
        return discharge(kw);
    }

    /**
     * 按固定功率充电
     *
     * @param kw 充电功率 (kW)
     * @return 实际充电量 (kWh)
     */
    public double chargeAtRate(double kw) {
        if (kw <= 0) {
            return 0;
        }
        return charge(kw);
    }

    /**
     * 应用自放电衰减
     */
    protected void applySelfDischarge() {
        soc *= (1.0 - selfDischarge);
        if (soc < 0) {
            soc = 0;
        }
    }

    /** @return 累计购买成本 */
    public double getTotalBuyCost() {
        return totalBuyCost;
    }

    /** @return 累计销售收入 */
    public double getTotalSellRevenue() {
        return totalSellRevenue;
    }

    /** @return SoC 百分比 (0-100) */
    public double getSocPercent() {
        return (soc / capacity) * 100.0;
    }
}