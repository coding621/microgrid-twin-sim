package com.microgrid.sim.simulation.base;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.service.EventControlService;
import com.microgrid.sim.service.LogAggregatorService;

/**
 * 负荷实体抽象基类 。
 *
 * <p>封装了用电负荷的公共行为：名义功耗、当前需求计算（含尖峰调整）等。
 *
 * @author Coding
 */
public abstract class AbstractLoadEntity extends AbstractSimEntity {

    /** 名义功耗 (kW) */
    protected final double nominalLoad;
    /** 当前需求 (kW) */
    protected volatile double currentDemand = 0.0;

    /**
     * 构造函数
     *
     * @param name        实体名称
     * @param logger      日志服务
     * @param registry    状态注册中心
     * @param events      事件控制服务
     * @param nominalLoad 名义功耗 (kW)
     */
    public AbstractLoadEntity(String name, LogAggregatorService logger,
                              AgentStateRegistry registry, EventControlService events,
                              double nominalLoad) {
        super(name, logger, registry, events);
        this.nominalLoad = nominalLoad;
    }

    /** 负荷本身不发电 */
    @Override
    public double getProduction() {
        return 0.0;
    }

    /** @return 当前电力需求 (kW) */
    @Override
    public double getDemand() {
        return currentDemand;
    }

    /** @return 名义功耗 (kW) */
    public double getNominalLoad() {
        return nominalLoad;
    }

    /**
     * 获取经过尖峰调整后的需求
     *
     * @return 含尖峰倍率的需求 (kW)
     */
    protected double getSpikeAdjustedDemand() {
        int multiplier = events.checkLoadSpike(name);
        if (multiplier > 1) {
            return nominalLoad * multiplier;
        }
        return nominalLoad;
    }
}