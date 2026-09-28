package com.microgrid.sim.service;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.simulation.*;
import com.microgrid.sim.ws.dto.MetricsMessage;
import com.microgrid.sim.ws.dto.TickDataMessage;
import com.microgrid.sim.ws.service.MetricsPublishingService;
import com.microgrid.sim.ws.service.TickPublishingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真引擎服务
 *
 * <p>负责：
 * <ul>
 *   <li>解析配置并创建所有仿真实体</li>
 *   <li>管理仿真主循环（基于 ScheduledExecutorService 的单线程调度）</li>
 *   <li>每 tick 按顺序调用：实体 onTick → 聚合器 onTick → 发布数据</li>
 *   <li>支持暂停/恢复/加速/重启</li>
 * </ul>
 *
 *
 * @author Coding
 */
@Service
public class SimulationEngineService {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngineService.class);

    /* ---- 依赖注入 ---- */
    private final SimulationConfigService configService;
    private final SimulationControlService controlService;
    private final EventControlService eventService;
    private final LogAggregatorService logAggregator;
    private final AgentStateRegistry stateRegistry;
    private final WeatherService weatherService;
    private final TickPublishingService tickPublisher;
    private final MetricsPublishingService metricsPublisher;

    /* ---- 仿真实体 ---- */
    private final List<LoadService> loads = new ArrayList<>();
    private final List<EnergySourceService> sources = new ArrayList<>();
    private final List<EnergyStorageService> storages = new ArrayList<>();
    private ExternalEnergySourceService externalSource;
    private AggregatorService aggregator;

    /* ---- 运行控制 ---- */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> tickTask;
    private final AtomicLong tickNumber = new AtomicLong(0);
    private volatile boolean running = false;

    /**
     * 构造函数注入
     *
     * @param configService     配置服务
     * @param controlService    控制服务
     * @param eventService      事件服务
     * @param logAggregator     日志服务
     * @param stateRegistry     状态注册中心
     * @param weatherService    天气服务
     * @param tickPublisher     Tick 发布服务
     * @param metricsPublisher  指标发布服务
     */
    public SimulationEngineService(SimulationConfigService configService,
                                   SimulationControlService controlService,
                                   EventControlService eventService,
                                   LogAggregatorService logAggregator,
                                   AgentStateRegistry stateRegistry,
                                   WeatherService weatherService,
                                   TickPublishingService tickPublisher,
                                   MetricsPublishingService metricsPublisher) {
        this.configService = configService;
        this.controlService = controlService;
        this.eventService = eventService;
        this.logAggregator = logAggregator;
        this.stateRegistry = stateRegistry;
        this.weatherService = weatherService;
        this.tickPublisher = tickPublisher;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 启动仿真 - 解析配置、创建实体并启动主循环。
     *
     * @throws RuntimeException 如果启动失败
     */
    public synchronized void start() {
        if (running) {
            throw new IllegalStateException("仿真已在运行中");
        }

        try {
            List<Map<String, Object>> agentDefs = configService.getValidatedAgentDefinitions();
            buildEntities(agentDefs);
            initAggregator();

            log.info("[START] 仿真启动。共创建 {} 个实体。", agentDefs.size());
            running = true;
            tickNumber.set(0);
            controlService.resume();
            scheduleNextTick();

        } catch (Exception e) {
            log.error("仿真启动失败:", e);
            throw new RuntimeException("仿真无法启动: " + e.getMessage(), e);
        }
    }

    /**
     * 停止仿真并清理所有实体
     */
    public synchronized void stop() {
        running = false;
        if (tickTask != null && !tickTask.isDone()) {
            tickTask.cancel(false);
        }
        loads.clear();
        sources.clear();
        storages.clear();
        externalSource = null;
        aggregator = null;
        stateRegistry.resetErrorAccumulators();
        logAggregator.clearLogs();
        log.info("[STOP] 仿真已停止。");
    }

    /**
     * 暂停仿真
     */
    public void pause() {
        controlService.pause();
        log.info("[PAUSE] 仿真已暂停。");
    }

    /**
     * 恢复仿真
     */
    public void resume() {
        controlService.resume();
        if (running && (tickTask == null || tickTask.isDone())) {
            scheduleNextTick();
        }
        log.info("[RESUME] 仿真已恢复。");
    }

    /**
     * 设置加速因子
     *
     * @param factor 加速倍数 (>0)
     */
    public void setSpeedUpFactor(double factor) {
        controlService.setSpeedUpFactor(factor);
        log.info("[SPEED] 加速因子设置为 {}", factor);
    }

    /* ==================== 实体构建 ==================== */

    /**
     * 根据配置定义创建所有仿真实体
     */
    private void buildEntities(List<Map<String, Object>> defs) {
        for (Map<String, Object> d : defs) {
            String type = (String) d.get("type");
            String name = (String) d.get("name");

            switch (type) {
                case "energySource" -> buildEnergySource(d, name);
                case "energyStorage" -> buildEnergyStorage(d, name);
                case "load" -> buildLoad(d, name);
                default -> log.warn("未知实体类型: {}", type);
            }
        }

        // 外部电网
        double extCap = configService.getExternalSourceCap();
        double extCost = configService.getExternalSourceCost();
        externalSource = new ExternalEnergySourceService(extCap, extCost);
    }

    /**
     * 构建光伏发电源
     */
    private void buildEnergySource(Map<String, Object> d, String name) {
        double panels = ((Number) d.get("noOfPanels")).doubleValue();
        double efficiency = ((Number) d.get("efficiency")).doubleValue();
        double area = ((Number) d.get("area")).doubleValue();
        double tempCoeff = ((Number) d.get("tempCoeff")).doubleValue();
        double noct = ((Number) d.get("noct")).doubleValue();

        EnergySourceService s = new EnergySourceService(name, logAggregator,
                stateRegistry, eventService, weatherService,
                panels, efficiency, area, tempCoeff, noct);
        sources.add(s);
        logAggregator.log(name, "发电源已创建");
    }

    /**
     * 构建储能电池
     */
    private void buildEnergyStorage(Map<String, Object> d, String name) {
        double capacity = ((Number) d.get("capacity")).doubleValue();
        double etaCharge = ((Number) d.get("etaCharge")).doubleValue();
        double etaDischarge = ((Number) d.get("etaDischarge")).doubleValue();
        double cRate = ((Number) d.get("cRate")).doubleValue();
        double selfDischarge = ((Number) d.get("selfDischarge")).doubleValue();
        double initialSoc = ((Number) d.get("initialSoC")).doubleValue();

        EnergyStorageService s = new EnergyStorageService(name, logAggregator,
                stateRegistry, eventService,
                capacity, etaCharge, etaDischarge, cRate, selfDischarge, initialSoc);
        storages.add(s);
        logAggregator.log(name, "电池已创建");
    }

    /**
     * 构建负荷
     */
    private void buildLoad(Map<String, Object> d, String name) {
        double nominalLoad = ((Number) d.get("nominalLoad")).doubleValue();
        LoadService l = new LoadService(name, logAggregator,
                stateRegistry, eventService, nominalLoad);
        loads.add(l);
        logAggregator.log(name, "负荷已创建");
    }

    /**
     * 初始化聚合器
     */
    private void initAggregator() {
        double extCost = configService.getExternalSourceCost();
        aggregator = new AggregatorService(logAggregator, stateRegistry, configService, extCost);
        aggregator.init(loads, sources, externalSource);
        for (EnergyStorageService s : storages) {
            aggregator.addStorage(s);
        }
    }

    /* ==================== 仿真主循环 ==================== */

    /**
     * 调度下一个 tick - 基于配置的延迟时间
     */
    private void scheduleNextTick() {
        if (!running) return;

        long delay = controlService.getSimulationDelay();
        tickTask = scheduler.schedule(this::executeOneTick, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 执行一个 tick 的完整流程
     */
    private void executeOneTick() {
        try {
            long tick = tickNumber.incrementAndGet();

            // [1] 停电期间跳过执行
            if (eventService.inBlackoutAndTick()) {
                scheduleNextTick();
                return;
            }

            // [2] 所有实体逐 tick 执行
            for (LoadService l : loads) {
                l.onTick(tick);
            }
            for (EnergySourceService s : sources) {
                s.onTick(tick);
            }
            for (EnergyStorageService s : storages) {
                s.onTick(tick);
            }

            // [3] 聚合器处理（CNP协商、预测、规划、执行）
            if (aggregator != null) {
                aggregator.onTick(tick);
            }

            // [4] 发布实时 tick 数据
            publishTickData(tick);

            // [5] 每隔 metricsPerNTicks 个 tick 发布指标
            if (tick % configService.getMetricsPerNTicks() == 0) {
                publishMetrics(tick);
            }

        } catch (Exception e) {
            log.error("Tick 执行异常:", e);
        }

        // [6] 检查暂停状态 → 调度下一 tick
        if (controlService.isPaused()) {
            tickTask = null; // 暂停时不调下一 tick
        } else {
            scheduleNextTick();
        }
    }

    /**
     * 发布当前 tick 的实时数据到 WebSocket
     */
    private void publishTickData(long tick) {
        TickDataMessage msg = new TickDataMessage();
        msg.setTickNumber(tick);
        msg.setAgentStates(stateRegistry.all());
        msg.setPredictedLoadKw(stateRegistry.getPredLoad());
        msg.setPredictedPvKw(stateRegistry.getPredPv());
        // 计算误差增量
        if (!Double.isNaN(stateRegistry.getPredLoad()) && aggregator != null) {
            double errLoad = aggregator.getConsumedThisTick() - stateRegistry.getPredLoad();
            double errPv = aggregator.getProducedThisTick() - stateRegistry.getPredPv();
            msg.setErrorLoadKw(errLoad);
            msg.setErrorPvKw(errPv);
            stateRegistry.addErrorSample(errLoad, errPv);
        }
        msg.setFanLoLoad(stateRegistry.getFanLoLoad());
        msg.setFanHiLoad(stateRegistry.getFanHiLoad());
        msg.setFanLoPv(stateRegistry.getFanLoPv());
        msg.setFanHiPv(stateRegistry.getFanHiPv());
        tickPublisher.publish(msg);
    }

    /**
     * 发布聚合指标到 WebSocket
     */
    private void publishMetrics(long tick) {
        if (aggregator == null) {
            return;
        }

        MetricsMessage msg = new MetricsMessage();
        msg.setTickNumber(tick);
        msg.setTotalProduced(aggregator.getTotalProduced());
        msg.setTotalConsumed(aggregator.getTotalConsumed());
        msg.setCnpNegotiations(aggregator.getTotalCnpNegotiations());
        msg.setTotalProducedPerNTicks(aggregator.getProducedThisTick());
        msg.setTotalDemandPerNTicks(aggregator.getConsumedThisTick());
        msg.setRmseLoadKw(stateRegistry.currentRmseLoad());
        msg.setRmsePvKw(stateRegistry.currentRmsePv());
        msg.setForecastLoadKw(stateRegistry.getForecastLoad());
        msg.setForecastPvKw(stateRegistry.getForecastPv());

        // 绿色能源占比
        double green = stateRegistry.getTotalGreenEnergyGeneration();
        double total = Math.max(1, aggregator.getConsumedThisTick());
        msg.setGreenEnergyRatioPct(green / total * 100.0);

        metricsPublisher.publish(msg);
    }

    /* ==================== 状态查询 ==================== */

    /** @return 仿真是否正在运行 */
    public boolean isRunning() {
        return running;
    }

    /** @return 仿真是否处于暂停状态 */
    public boolean isPaused() {
        return controlService.isPaused();
    }

    /** @return 当前 tick 序号 */
    public long getCurrentTick() {
        return tickNumber.get();
    }
}