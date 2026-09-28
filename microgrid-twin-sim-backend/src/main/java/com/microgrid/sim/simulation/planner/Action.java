package com.microgrid.sim.simulation.planner;

/**
 * 调度动作记录 - 描述在某个时间步上执行的具体调度指令。
 *
 * <p>每条动作表示：在 tickOffset 时间步，对某个目标实体执行充电/放电，
 * 或从外部电网购入电力。
 *
 * @author Coding
 * @param tickOffset  相对于当前时刻的时间偏移（0 = 立即执行）
 * @param target      目标实体名称（电池名称或 "External"）
 * @param chargeKw    充电功率 (kW)，正数=充电，负数=放电
 * @param extImportKw 外部电网购入功率 (kW)
 */
public record Action(int tickOffset,
                     String target,
                     double chargeKw,
                     double extImportKw) {
}