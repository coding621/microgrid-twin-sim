package com.microgrid.sim.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件控制服务 - 管理仿真中的随机事件（故障、负荷尖峰、停电）。
 *
 * <p>提供以下能力：
 * <ul>
 *   <li>组件故障管理（定时自动恢复）</li>
 *   <li>负荷尖峰注入（定时自动恢复）</li>
 *   <li>外部停电（黑启动）管理</li>
 * </ul>
 *
 * @author Coding
 */
@Getter
@Service
public class EventControlService {

    /**
     * 负荷尖峰参数对 - 包含倍率和剩余 tick 数
     */
    @Setter
    @Getter
    class IntPair {
        int rate;
        int ticks;

        public IntPair(int rate, int ticks) {
            this.rate = rate;
            this.ticks = ticks;
        }
    }

    /** 故障组件映射，key=组件名称, value=剩余故障 tick 数 */
    private final Map<String, Integer> brokenComponents = new ConcurrentHashMap<>();

    /** 负荷尖峰映射，key=负荷名称, value=(倍率, 剩余tick) */
    private final Map<String, IntPair> loadSpikes = new ConcurrentHashMap<>();

    /** 停电剩余 tick 数 */
    private final AtomicLong blackoutTicksRemaining = new AtomicLong(0);

    /**
     * 添加故障组件
     *
     * @param name  组件名称
     * @param ticks 故障持续 tick 数
     */
    public void addBrokenComponent(String name, int ticks) {
        brokenComponents.put(name, ticks);
    }

    /**
     * 检查组件是否处于故障状态，并自动递减剩余 tick
     *
     * @param name 组件名称
     * @return true 如果当前 tick 组件处于故障状态
     */
    public boolean isBroken(String name) {
        Integer remaining = brokenComponents.get(name);
        if (remaining == null || remaining < 1) {
            return false;
        }
        remaining--;
        if (remaining == 0) {
            brokenComponents.remove(name); // 故障恢复
        } else {
            brokenComponents.put(name, remaining);
        }
        return true;
    }

    /**
     * 添加负荷尖峰
     *
     * @param name  负荷名称
     * @param ticks 持续 tick 数
     * @param rate  能耗倍率
     */
    public void addLoadSpike(String name, int ticks, int rate) {
        loadSpikes.put(name, new IntPair(rate, ticks));
    }

    /**
     * 检查指定负荷是否有尖峰，并自动递减剩余 tick
     *
     * @param name 负荷名称
     * @return 当前能耗倍率（无尖峰时返回 1）
     */
    public int checkLoadSpike(String name) {
        return loadSpikes.computeIfPresent(name, (k, p) -> {
            if (--p.ticks <= 0) {
                return null; // 尖峰结束
            }
            return p;
        }) != null
                ? loadSpikes.get(name).rate
                : 1;
    }

    /**
     * 启动停电（黑启动）
     *
     * @param ticks 停电持续 tick 数
     */
    public void startBlackout(long ticks) {
        blackoutTicksRemaining.set(ticks);
    }

    /**
     * 检查是否处于停电状态并递减计时器
     *
     * @return true 如果当前处于停电状态
     */
    public boolean inBlackoutAndTick() {
        long rem = blackoutTicksRemaining.get();
        if (rem > 0) {
            blackoutTicksRemaining.decrementAndGet();
            return true;
        }
        return false;
    }

    /** @return 停电剩余 tick 数 */
    public long getBlackoutRemaining() {
        return blackoutTicksRemaining.get();
    }
}