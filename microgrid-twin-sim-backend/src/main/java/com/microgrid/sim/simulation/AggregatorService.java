package com.microgrid.sim.simulation;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.service.EventControlService;
import com.microgrid.sim.service.LogAggregatorService;
import com.microgrid.sim.service.SimulationConfigService;
import com.microgrid.sim.simulation.aggregator.AggregatorMetaStore;
import com.microgrid.sim.simulation.forecast.Forecaster;
import com.microgrid.sim.simulation.forecast.ForecasterRegistry;
import com.microgrid.sim.simulation.history.HistoryBuffer;
import com.microgrid.sim.simulation.models.Proposal;
import com.microgrid.sim.simulation.planner.Action;
import com.microgrid.sim.simulation.planner.ActionQueue;
import com.microgrid.sim.simulation.planner.DeterministicPlanner;
import com.microgrid.sim.simulation.scenario.*;
import com.microgrid.sim.ws.dto.TickDataMessage;

import java.util.*;

/**
 * 聚合器服务
 *
 * <p>微电网的核心调度中枢，功能包括：
 * <ul>
 *   <li>历史数据收集与概率预测</li>
 *   <li>确定性线性规划调度</li>
 *   <li>短期实时电池管理</li>
 *   <li>处理生产/消费事件</li>
 *   <li>处理短fall协商</li>
 *   <li>处理盈余协商</li>
 * </ul>
 *
 *
 * @author Coding
 */
public class AggregatorService {

    /* ---- 依赖注入 ---- */
    private final LogAggregatorService logger;
    private final AgentStateRegistry registry;
    private final SimulationConfigService configService;
    private final double extCostKwh;

    /* ---- 数据存储 ---- */
    /** 电池元数据存储（静态） */
    private final AggregatorMetaStore metaStore = new AggregatorMetaStore();
    /** 历史数据环形缓冲区 */
    private HistoryBuffer histBuf;
    /** 概率预测器 */
    private Forecaster forecaster;
    /** 场景生成器 */
    private ScenarioGenerator scenarioGen;
    /** 确定性规划器 */
    private DeterministicPlanner planner;
    /** 调度动作队列 */
    private final ActionQueue queue = new ActionQueue();

    /* ---- 实例子列表 ---- */
    /** 所有负荷实体 */
    private List<LoadService> loads = new ArrayList<>();
    /** 所有发电源实体 */
    private List<EnergySourceService> sources = new ArrayList<>();
    /** 所有储能电池实体 */
    private final List<EnergyStorageService> storages = new ArrayList<>();
    /** 外部能源 */
    private ExternalEnergySourceService externalSource;

    /* ---- 配置参数 ---- */
    private int H_hist;
    private int H_pred;
    private int replanEvery;
    private double epsilonBreak;
    private boolean enablePredictive;

    /* ---- 运行状态 ---- */
    private long ticksSinceReplan = 0;
    /** 累计总发电量 (kWh) */
    private double totalProduced = 0;
    /** 累计总耗电量 (kWh) */
    private double totalConsumed = 0;
    /** CNP 协商累计 */
    private double totalCnpNegotiations = 0;

    /** 每个 tick 的总发电量 */
    private double producedThisTick = 0;
    /** 每个 tick 的总耗电量 */
    private double consumedThisTick = 0;

    /**
     * 构造函数
     *
     * @param logger        日志服务
     * @param registry      状态注册中心
     * @param configService 仿真配置服务
     * @param extCostKwh    外部电价
     */
    public AggregatorService(LogAggregatorService logger, AgentStateRegistry registry,
                             SimulationConfigService configService, double extCostKwh) {
        this.logger = logger;
        this.registry = registry;
        this.configService = configService;
        this.extCostKwh = extCostKwh;
    }

    /**
     * 初始化聚合器 - 配置预测/建模参数，注册电池元数据
     *
     * @param loads   所有负荷实体
     * @param sources 所有发电源实体
     * @param ext     外部能源服务
     */
    public void init(List<LoadService> loads, List<EnergySourceService> sources,
                     ExternalEnergySourceService ext) {
        this.loads = loads;
        this.sources = sources;
        this.externalSource = ext;

        // 读取预测参数（带默认值，防止前端配置缺少字段）
        Map<String, Object> fc = configService.getForecastParams();
        this.H_hist = toInt(fc.get("H_hist"), 24);
        this.H_pred = toInt(fc.get("H_pred"), 4);
        this.replanEvery = toInt(fc.get("replanEvery"), 2);
        this.epsilonBreak = toDouble(fc.get("epsilonBreak"), 20.0);
        this.enablePredictive = toInt(fc.get("enablePredictive"), 1) != 0;

        // 初始化历史缓冲区和预测器（通过注册表动态选择算法）
        this.histBuf = new HistoryBuffer(H_hist);
        String forecastMethod = toStr(fc.get("forecastMethod"));
        this.forecaster = ForecasterRegistry.create(forecastMethod, H_pred);
        this.forecaster.setModelSavePath("data/models");
        log("预测方法: " + forecastMethod);

        // 初始化场景生成器
        boolean useMC = toInt(fc.get("useMC"), 0) != 0;
        this.scenarioGen = useMC
                ? new MonteCarloGenerator(H_pred, 50)
                : new QuantileTreeGenerator(H_pred);

        log("初始化完成。使用" + (useMC ? "蒙特卡洛" : "分位数树") + "场景生成器。");
    }

    /**
     * 添加电池到聚合器的管理中
     *
     * @param storage 电池实体
     */
    public void addStorage(EnergyStorageService storage) {
        storages.add(storage);

        // 注册电池静态元数据
        metaStore.addBattery(storage.getName(),
                new AggregatorMetaStore.BatteryMeta(
                        storage.getSoc() / storage.getSocPercent() * 100.0,
                        // 从storage获取eta信息较复杂，在此使用默认
                        0.94, 0.92, 0.5));
    }

    /**
     * 更新电池元数据（含运行时状态）
     *
     * @param storage 电池实体
     */
    private void updateBatteryMeta(EnergyStorageService storage) {
        String name = storage.getName();
        AggregatorMetaStore.BatteryMeta existing = metaStore.getBattery(name);
        if (existing == null) {
            return;
        }

        metaStore.addBattery(name, new AggregatorMetaStore.BatteryMeta(
                storage.getSoc() / (storage.getSocPercent() / 100.0),
                existing.etaC(), existing.etaD(), existing.cRate()));
    }

    /**
     * 每个 tick 的主行为：
     * <ol>
     *   <li>收集当前生产/消费数据</li>
     *   <li>处理短fall/盈余 CNP 协商</li>
     *   <li>将数据写入历史缓冲区</li>
     *   <li>到达重规划时刻时执行预测和线性规划</li>
     *   <li>执行队列中的下一个调度动作</li>
     *   <li>更新注册中心的状态快照</li>
     * </ol>
     *
     * @param tick 当前 tick 序号
     */
    public void onTick(long tick) {
        // [1] 收集实时生产/消费数据
        producedThisTick = sources.stream()
                .filter(s -> !s.isBroken())
                .mapToDouble(EnergySourceService::getProduction)
                .sum();

        consumedThisTick = loads.stream()
                .filter(l -> !l.isBroken())
                .mapToDouble(LoadService::getDemand)
                .sum();

        double netNow = producedThisTick - consumedThisTick;

        // [2] CNP 协商处理 (替代 HandleShortfallCNP 和 HandleSurplusCNP)
        executeCnpNegotiation(netNow);

        totalProduced += producedThisTick;
        totalConsumed += consumedThisTick;
        totalCnpNegotiations = storages.stream()
                .mapToInt(EnergyStorageService::getCnpNegotiations).sum()
                + (externalSource != null ? externalSource.getCnpNegotiations() : 0);

        // [3] 写入历史缓冲区
        histBuf.push(consumedThisTick, producedThisTick,
                0.0, 0.0, getTotalRawSoc());

        // [4] 到达重规划时刻：预测 + 线性规划
        if (enablePredictive && ++ticksSinceReplan >= replanEvery && histBuf.isFull()) {
            ticksSinceReplan = 0;
            replanAndForecast(tick);
        }

        // [5] 执行下一个调度动作
        executeNextAction();

        // [6] 更新注册中心状态
        updateRegistryStates(tick);
    }

    /**
     * 执行 CNP 协商（短期实时电池管理）。
     * <p>如果能量不足（短fall），尝试让电池放电 → 外部电网（逐步成本）。
     * 如果能量盈余，尝试让电池充电 → 外部电网出售盈余。
     *
     * @param netNow 当前净能量 (生产 - 消费, kW)
     */
    private void executeCnpNegotiation(double netNow) {
        double threshold = 0.5;

        if (netNow < -threshold) {
            // 短fall：需求 - 产量
            handleShortfall(-netNow);
        } else if (netNow > threshold) {
            // 盈余：产量 - 需求
            handleSurplus(netNow);
        }
    }

    /**
     * 处理电力短fall (替代 HandleShortfallCNP)。
     *
     * <p>策略（逐步调用）：
     * <ol>
     *   <li>请求所有电池放电</li>
     *   <li>从外部电网购入剩余缺额</li>
     * </ol>
     *
     * @param shortfallKw 短fall量 (kW)
     */
    private void handleShortfall(double shortfallKw) {
        double remaining = shortfallKw;

        // [1] 收集各电池的放电提案
        List<Proposal> propList = new ArrayList<>();
        for (EnergyStorageService storage : storages) {
            Proposal p = storage.makeDischargeProposal(extCostKwh, remaining);
            propList.add(p);
            storage.incrementCnpNegotiations();
        }

        // [2] 按成本升序排序（便宜优先）
        propList.sort(Comparator.comparingDouble(Proposal::getCost)
                .thenComparingInt(Proposal::getArrivalIndex));

        // [3] 按成本从低到高接受放电
        for (Proposal pp : propList) {
            if (remaining <= 0) {
                break;
            }
            double accepted = Math.min(pp.getAmount(), remaining);
            pp.setAcceptedAmount(accepted);
            remaining -= accepted;

            // 找到对应电池并执行放电
            for (EnergyStorageService storage : storages) {
                if (storage.getName().equals(pp.getSender())) {
                    storage.discharge(accepted);
                    break;
                }
            }
        }

        // [4] 剩余缺额从外部电网购入
        if (remaining > 0 && externalSource != null) {
            externalSource.incrementCnpNegotiations();
            double extImport = Math.min(remaining, externalSource.getMaxPower());
            // 外部电网购电直接计入成本（简化处理）
            log("从外部电网购入 " + String.format("%.2f", extImport) + " kWh 补缺");
        }

        log("处理短fall: " + String.format("%.2f", shortfallKw)
                + " kW, 剩余未满足: " + String.format("%.2f", remaining) + " kW");
    }

    /**
     * 处理电力盈余 (替代 HandleSurplusCNP)。
     *
     * <p>策略：
     * <ol>
     *   <li>请求所有电池充电吸收盈余</li>
     *   <li>剩余盈余出售给外部电网</li>
     * </ol>
     *
     * @param surplusKw 盈余量 (kW)
     */
    private void handleSurplus(double surplusKw) {
        double remaining = surplusKw;

        // [1] 收集各电池的充电提案
        List<Proposal> propList = new ArrayList<>();
        for (EnergyStorageService storage : storages) {
            Proposal p = storage.makeChargeProposal(extCostKwh, remaining);
            propList.add(p);
            storage.incrementCnpNegotiations();
        }

        // [2] 按收入降序 + 成本升序排序（利润最大化）
        propList.sort((a, b) -> {
            int cmp = Double.compare(a.getCost(), b.getCost());
            if (cmp == 0) {
                cmp = Integer.compare(a.getArrivalIndex(), b.getArrivalIndex());
            }
            return cmp;
        });

        // [3] 按序接受
        for (Proposal pp : propList) {
            if (remaining <= 0) {
                break;
            }
            double accepted = Math.min(pp.getAmount(), remaining);
            pp.setAcceptedAmount(accepted);
            remaining -= accepted;

            // 找到对应电池并执行充电
            for (EnergyStorageService storage : storages) {
                if (storage.getName().equals(pp.getSender())) {
                    storage.charge(accepted);
                    break;
                }
            }
        }

        // [4] 剩余盈余卖给外部电网
        if (remaining > 0 && externalSource != null) {
            externalSource.incrementCnpNegotiations();
            log("剩余盈余 " + String.format("%.2f", remaining) + " kW 出售给外部电网");
        }
    }

    /**
     * 执行预测和重规划
     * <ol>
     *   <li>使用历史数据更新概率预测模型</li>
     *   <li>输出预测结果到注册中心</li>
     *   <li>生成场景</li>
     *   <li>求解确定性规划并填充动作队列</li>
     * </ol>
     */
    private void replanAndForecast(long tick) {
        try {
            // [1] 更新预测模型
            forecaster.update(histBuf.getLoad(), histBuf.getPv(), histBuf.getTemp());

            // [2] 预测并输出
            double[][] pvQuantiles = forecaster.predictPv();
            double[][] loadQuantiles = forecaster.predictLoad();

            registry.setForecast(loadQuantiles[1][0], pvQuantiles[1][0]);
            registry.updateForecast(loadQuantiles[1], pvQuantiles[1]);
            registry.setFanChart(loadQuantiles[0], loadQuantiles[2],
                    pvQuantiles[0], pvQuantiles[2]);

            // [3] 生成场景
            List<Scenario> scenarios = scenarioGen.generate(loadQuantiles, pvQuantiles);
            if (scenarios.isEmpty()) {
                return;
            }

            // [4] 找中位场景
            Scenario median = scenarios.get(0);
            double maxP = median.prob();
            for (Scenario s : scenarios) {
                if (s.prob() > maxP) {
                    maxP = s.prob();
                    median = s;
                }
            }

            // [5] 求解中位场景的确定性规划
            planner = new DeterministicPlanner(H_pred, metaStore.allBatteries(),
                    externalSource != null ? externalSource.getMaxPower() : 500.0);
            List<Action> actions = planner.solve(median, getTotalRawSoc());
            queue.clear();
            queue.addAll(actions);

            log("重规划完成，生成 " + actions.size() + " 个调度动作");

        } catch (Exception e) {
            logger.log("AggregatorService", "重规划异常: " + e.getMessage());
        }
    }

    /**
     * 执行队列中的下一个调度动作
     */
    private void executeNextAction() {
        Action act = queue.pop();
        if (act == null) {
            return;
        }

        if ("External".equals(act.target())) {
            // 外部电网交互已在 CNP 中处理
            return;
        }

        // 查找目标电池并执行充放电
        for (EnergyStorageService s : storages) {
            if (s.getName().equals(act.target())) {
                if (act.chargeKw() > 0) {
                    s.charge(act.chargeKw());
                } else if (act.chargeKw() < 0) {
                    s.discharge(-act.chargeKw());
                }
                break;
            }
        }
    }

    /**
     * 更新注册中心中的所有实体状态快照
     */
    private void updateRegistryStates(long tick) {
        for (LoadService l : loads) {
            TickDataMessage.AgentState st = new TickDataMessage.AgentState();
            st.setCnpNegotiations(0);
            st.setDemand(l.getDemand());
            st.setProduction(0);
            st.setStateOfCharge(0);
            st.setBroken(l.isBroken());
            registry.update(l.getName(), st);
        }
        for (EnergySourceService s : sources) {
            TickDataMessage.AgentState st = new TickDataMessage.AgentState();
            st.setCnpNegotiations(0);
            st.setDemand(0);
            st.setProduction(s.getProduction());
            st.setStateOfCharge(0);
            st.setBroken(s.isBroken());
            registry.update(s.getName(), st);
        }
        for (EnergyStorageService s : storages) {
            TickDataMessage.AgentState st = new TickDataMessage.AgentState();
            st.setCnpNegotiations(s.getCnpNegotiations());
            st.setDemand(0);
            st.setProduction(0);
            st.setStateOfCharge(s.getSoc());
            st.setBroken(s.isBroken());
            registry.update(s.getName(), st);
        }
    }

    /** @return 所有电池的总 SoC (kWh) */
    private double getTotalRawSoc() {
        return storages.stream().mapToDouble(EnergyStorageService::getSoc).sum();
    }

    /** @return 累计总发电量 (kWh) */
    public double getTotalProduced() {
        return totalProduced;
    }

    /** @return 累计总耗电量 (kWh) */
    public double getTotalConsumed() {
        return totalConsumed;
    }

    /** @return 累计 CNP 协商次数 */
    public double getTotalCnpNegotiations() {
        return totalCnpNegotiations;
    }

    /** @return 当前 tick 发电量 (kW) */
    public double getProducedThisTick() {
        return producedThisTick;
    }

    /** @return 当前 tick 耗电量 (kW) */
    public double getConsumedThisTick() {
        return consumedThisTick;
    }

    /* ---- 工具方法 ---- */

    /**
     * 安全地将 Object 转为 String，null 时返回默认值
     */
    private static String toStr(Object value) {
        if (value == null) {
            return ForecasterRegistry.defaultMethod();
        }
        return value.toString();
    }

    /**
     * 安全地将 Object 转为 int，null 时返回默认值
     */
    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    /**
     * 安全地将 Object 转为 double，null 时返回默认值
     */
    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return defaultValue;
    }

    /* ---- 日志方法 ---- */
    private void log(String msg) {
        logger.log("AggregatorService", msg);
    }
}