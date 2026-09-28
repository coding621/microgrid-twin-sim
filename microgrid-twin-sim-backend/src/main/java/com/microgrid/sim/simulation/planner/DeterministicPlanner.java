package com.microgrid.sim.simulation.planner;

import com.microgrid.sim.simulation.aggregator.AggregatorMetaStore;
import com.microgrid.sim.simulation.scenario.Scenario;
import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性规划器 - 基于 OR-Tools LP（线性规划）求解中位场景的最优调度方案。
 *
 * <p>在给定预测视野内，优化各电池的充放电决策和外部电网交互，目标是最小化能量损失。
 * 约束包括：功率平衡、电池 SoC 动态方程、充放电功率上下限等。
 *
 * <p>业务逻辑与原 JADE 版本完全一致。
 *
 * @author Coding
 */
public final class DeterministicPlanner {

    /** 预测视野长度（时间步数） */
    private final int H;
    /** 所有电池的元数据映射 */
    private final Map<String, AggregatorMetaStore.BatteryMeta> bats;
    /** 外部电网最大功率 (kW) */
    private final double extCapKw;

    /**
     * 构造函数
     *
     * @param horizon  预测视野（步数）
     * @param bats     电池元数据映射
     * @param extCapKw 外部电网容量 (kW)
     */
    public DeterministicPlanner(int horizon,
                                Map<String, AggregatorMetaStore.BatteryMeta> bats,
                                double extCapKw) {
        this.H = horizon;
        this.bats = bats;
        this.extCapKw = extCapKw;
        // 加载 OR-Tools 原生库
        Loader.loadNativeLibraries();
    }

    /**
     * 求解中位场景下的最优调度方案
     *
     * @param median 中位场景（负载和光伏的中位预测）
     * @param socNow 当前所有电池的总 SoC (kWh)
     * @return 调度动作列表，无解时返回空列表
     */
    public List<Action> solve(Scenario median, double socNow) {
        // 创建 LP 求解器（使用 GLOP）
        MPSolver solver = MPSolver.createSolver("GLOP");
        if (solver == null) {
            throw new IllegalStateException("No LP solver available");
        }

        /* ---- 变量定义 ---- */
        // 每个电池的充电变量 chg[b][k]、放电变量 dsg[b][k]、SoC 变量 soc[b][k]
        Map<String, MPVariable[]> chg = new HashMap<>();
        Map<String, MPVariable[]> dsg = new HashMap<>();
        Map<String, MPVariable[]> soc = new HashMap<>();

        for (var e : bats.entrySet()) {
            String id = e.getKey();
            var m = e.getValue();

            chg.put(id, solver.makeNumVarArray(H, 0, m.cRate() * m.capacity(), id + "_c"));
            dsg.put(id, solver.makeNumVarArray(H, 0, m.cRate() * m.capacity(), id + "_d"));
            soc.put(id, solver.makeNumVarArray(H + 1, 0, m.capacity(), id + "_e"));

            // SoC 动态方程: soc_{k+1} = soc_k + ηc*chg_k - dsg_k/ηd
            for (int k = 0; k < H; k++) {
                double nc = m.etaC();
                double nd = m.etaD();
                MPConstraint c = solver.makeConstraint(0, 0, "soc_" + id + "_" + k);
                c.setCoefficient(soc.get(id)[k + 1], 1);
                c.setCoefficient(soc.get(id)[k], -1);
                c.setCoefficient(chg.get(id)[k], -nc);
                c.setCoefficient(dsg.get(id)[k], +1 / nd);
            }
            // 初始 SoC = socNow 平均分配给各电池
            soc.get(id)[0].setBounds(socNow / bats.size(), socNow / bats.size());
        }

        // 外部电网交互变量（正=购入，负=溢出）
        MPVariable[] ext = solver.makeNumVarArray(H, -extCapKw, extCapKw, "Ext");

        /* ---- 功率平衡约束 ---- */
        for (int k = 0; k < H; k++) {
            MPConstraint bal = solver.makeConstraint(0, 0, "bal_" + k);
            bal.setCoefficient(ext[k], 1);
            // 等式右边 = 负载 - 光伏 (净需求)
            bal.setBounds(median.loadKw()[k] - median.pvKw()[k],
                    median.loadKw()[k] - median.pvKw()[k]);
            for (var id : bats.keySet()) {
                bal.setCoefficient(chg.get(id)[k], +1);
                bal.setCoefficient(dsg.get(id)[k], -1);
            }
        }

        /* ---- 目标函数：最小化能量损失 ---- */
        MPObjective obj = solver.objective();
        for (int k = 0; k < H; k++) {
            obj.setCoefficient(ext[k], 1);              // 购入成本权重 1
            for (var id : bats.keySet()) {
                obj.setCoefficient(chg.get(id)[k], 0.01); // 充电损耗代理
                obj.setCoefficient(dsg.get(id)[k], 0.01); // 放电损耗
            }
        }
        obj.setMinimization();

        /* ---- 求解 ---- */
        if (solver.solve() != MPSolver.ResultStatus.OPTIMAL) {
            return List.of();
        }

        /* ---- 转换为 Action 列表 ---- */
        List<Action> actions = new ArrayList<>();
        for (int k = 0; k < H; k++) {
            for (var id : bats.keySet()) {
                double c = chg.get(id)[k].solutionValue();
                double d = dsg.get(id)[k].solutionValue();
                if (c > 1e-3) {
                    actions.add(new Action(k, id, +c, 0));
                }
                if (d > 1e-3) {
                    actions.add(new Action(k, id, -d, 0));
                }
            }
            double ex = ext[k].solutionValue();
            if (Math.abs(ex) > 1e-3) {
                actions.add(new Action(k, "External", 0, ex));
            }
        }
        return actions;
    }
}