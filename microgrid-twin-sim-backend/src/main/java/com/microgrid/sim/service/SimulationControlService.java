package com.microgrid.sim.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

/**
 * 仿真控制服务 - 管理仿真运行的控制参数。
 *
 * <p>包括 tick 间隔、加速因子、暂停/恢复状态等。
 *
 * @author Coding
 */
@Getter
@Service
public class SimulationControlService {

    /** 基础 tick 间隔 (毫秒) */
    @Setter
    private int tickIntervalMillis = 1000;

    /** 加速因子（>1 加速，<1 减速） */
    @Setter
    private double speedUpFactor = 1;

    /** 仿真是否处于暂停状态 */
    private volatile boolean paused = false;

    /**
     * 获取实际的仿真延迟时间
     *
     * @return 考虑加速因子后的实际延迟 (毫秒)
     */
    public long getSimulationDelay() {
        return (long) (tickIntervalMillis / speedUpFactor);
    }

    /** 暂停仿真 */
    public void pause() {
        this.paused = true;
    }

    /** 恢复仿真 */
    public void resume() {
        this.paused = false;
    }
}