package com.microgrid.sim.simulation.planner;

import java.util.Collection;
import java.util.LinkedList;

/**
 * 动作队列 - 基于链表实现的FIFO动作队列。
 *
 * <p>用于存储由确定性规划器生成的调度动作，按顺序逐 tick 执行。
 *
 * @author Coding
 */
public final class ActionQueue {
    /** 底层链表存储 */
    private final LinkedList<Action> q = new LinkedList<>();

    /** 清空队列中的所有动作 */
    public void clear() {
        q.clear();
    }

    /**
     * 批量添加动作到队列末尾
     *
     * @param c 动作集合
     */
    public void addAll(Collection<Action> c) {
        q.addAll(c);
    }

    /**
     * 弹出队列头部动作
     *
     * @return 队列头部的动作，队列为空时返回 null
     */
    public Action pop() {
        return q.pollFirst();
    }

    /**
     * 检查队列是否为空
     *
     * @return true 如果队列为空
     */
    public boolean isEmpty() {
        return q.isEmpty();
    }
}