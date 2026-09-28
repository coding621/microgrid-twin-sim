package com.microgrid.sim.simulation;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.service.EventControlService;
import com.microgrid.sim.service.LogAggregatorService;
import com.microgrid.sim.simulation.base.AbstractLoadEntity;

/**
 * 负荷服务
 *
 * <p>实现简单的用电负荷模型：
 * <ul>
 *   <li>基本功耗 = 名义功耗 × 随机波动 (±20%)</li>
 *   <li>支持尖峰事件倍率调整</li>
 *   <li>支持故障状态（功耗降为0）</li>
 * </ul>
 *
 * @author Coding
 */
public class LoadService extends AbstractLoadEntity {

    /**
     * 构造函数
     *
     * @param name        实体名称
     * @param logger      日志服务
     * @param registry    状态注册中心
     * @param events      事件控制服务
     * @param nominalLoad 名义功耗 (kW)
     */
    public LoadService(String name, LogAggregatorService logger,
                       AgentStateRegistry registry, EventControlService events,
                       double nominalLoad) {
        super(name, logger, registry, events, nominalLoad);
    }

    /**
     * 每个 tick 的行为：计算消耗功率
     */
    @Override
    public void onTick(long tick) {
        if (isBroken()) {
            // 故障状态下功耗为0
            currentDemand = 0.0;
            return;
        }

        // 基本功耗 = 名义功耗 × [0.8, 1.2] 随机因子
        double demand = nominalLoad * (0.8 + 0.4 * Math.random());

        // 尖峰事件调整
        int multiplier = events.checkLoadSpike(name);
        if (multiplier > 1) {
            demand = nominalLoad * multiplier;
        }

        currentDemand = demand;
    }
}