package com.microgrid.sim.simulation.base;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.service.EventControlService;
import com.microgrid.sim.service.LogAggregatorService;

/**
 * 发电源抽象基类 。
 *
 * <p>封装了发电源的公共行为：发电量计算、名义容量管理等。
 * 具体发电模型由子类实现。
 *
 * @author Coding
 */
public abstract class AbstractEnergySourceEntity extends AbstractSimEntity {

    /** 当前发电量 (kW) */
    protected volatile double currentProduction = 0.0;
    /** 名义发电容量 (kW) */
    protected final double nominalCapacity;

    /**
     * 构造函数
     *
     * @param name            实体名称
     * @param logger          日志服务
     * @param registry        状态注册中心
     * @param events          事件控制服务
     * @param nominalCapacity 名义容量 (kW)
     */
    public AbstractEnergySourceEntity(String name, LogAggregatorService logger,
                                      AgentStateRegistry registry, EventControlService events,
                                      double nominalCapacity) {
        super(name, logger, registry, events);
        this.nominalCapacity = nominalCapacity;
    }

    /** @return 当前生产量 (kW) */
    @Override
    public double getProduction() {
        return currentProduction;
    }

    /** @return 名义容量 (kW) */
    public double getNominalCapacity() {
        return nominalCapacity;
    }

    /** 发电源没有用电需求 */
    @Override
    public double getDemand() {
        return 0.0;
    }

    /**
     * 计算在当前环境条件下的发电量
     *
     * @param irradiance 日照辐照度 (W/m²)
     * @param temperature 环境温度 (°C)
     * @return 发电量 (kW)
     */
    protected abstract double calculateProduction(double irradiance, double temperature);
}