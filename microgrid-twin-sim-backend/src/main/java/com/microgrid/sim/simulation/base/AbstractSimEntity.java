package com.microgrid.sim.simulation.base;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.service.EventControlService;
import com.microgrid.sim.service.LogAggregatorService;

/**
 * 仿真实体抽象基类 。
 *
 * <p>提供所有仿真实体的公共基础设施：名称管理、故障状态查询、日志记录等。
 * 每个实体在 onTick() 方法中定义自己的逐 tick 行为。
 *
 * @author Coding
 */
public abstract class AbstractSimEntity {

    /** 实体名称，用于标识和日志 */
    protected final String name;
    /** 日志聚合服务 */
    protected final LogAggregatorService logger;
    /** 状态注册中心 */
    protected final AgentStateRegistry registry;
    /** 事件控制服务（故障管理） */
    protected final EventControlService events;

    /**
     * 构造函数
     *
     * @param name     实体名称
     * @param logger   日志聚合服务
     * @param registry 状态注册中心
     * @param events   事件控制服务
     */
    public AbstractSimEntity(String name, LogAggregatorService logger,
                             AgentStateRegistry registry, EventControlService events) {
        this.name = name;
        this.logger = logger;
        this.registry = registry;
        this.events = events;
    }

    /** @return 实体名称 */
    public String getName() {
        return name;
    }

    /**
     * 检查当前实体是否处于故障状态
     *
     * @return true 如果当前 tick 实体处于故障状态
     */
    public boolean isBroken() {
        return events.isBroken(name);
    }

    /**
     * 每个 tick 的行为 - 由子类实现
     *
     * @param tick 当前 tick 序号
     */
    public abstract void onTick(long tick);

    /**
     * 获取当前实体的电力生产量 (kW)
     *
     * @return 当前生产量
     */
    public abstract double getProduction();

    /**
     * 获取当前实体的电力需求量 (kW)
     *
     * @return 当前需求量
     */
    public abstract double getDemand();
}