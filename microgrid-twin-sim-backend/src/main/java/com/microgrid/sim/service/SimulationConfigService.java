package com.microgrid.sim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真配置服务 - 负责加载、存储和提供仿真配置参数。
 *
 * <p>支持通过 REST API 动态加载 JSON 配置，并提供工具方法验证和检索配置。
 *
 * @author Coding
 */
@Getter
@Service
public class SimulationConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationConfigService.class);

    /** 当前仿真配置 */
    private Map<?, ?> config;

    /** 天气参数 */
    @Getter
    private volatile Map<String, Object> weatherParams = Map.of();

    /** 天气参数版本号（每次更新递增，用于通知实体重载） */
    private final AtomicLong weatherVersion = new AtomicLong(0);

    /**
     * 启动时自动从 classpath 加载默认仿真配置文件 simulation-config.json，
     * 以确保前端在未调用 /simulation/loadConfig 时也能正常启动仿真。
     */
    @PostConstruct
    public void init() {
        try {
            // 使用 ClassPathResource 加载 classpath 下的默认配置文件
            org.springframework.core.io.ClassPathResource resource =
                    new org.springframework.core.io.ClassPathResource("simulation-config.json");
            String defaultJson = new String(resource.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            setConfigFromString(defaultJson);
            logger.info("已从 classpath 加载默认仿真配置 simulation-config.json");
        } catch (Exception e) {
            logger.error("加载默认仿真配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法加载默认仿真配置 simulation-config.json", e);
        }
    }

    /**
     * 从 JSON 字符串设置仿真配置
     *
     * @param json 仿真配置 JSON 字符串
     */
    public void setConfigFromString(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> newCfg = mapper.readValue(json, Map.class);
            if (!newCfg.containsKey("simulation")) {
                throw new IllegalArgumentException("缺少 simulation 配置块");
            }
            this.config = newCfg;

            // 提取天气参数
            Object w = ((Map<?, ?>) newCfg.get("simulation")).get("weather");
            if (w instanceof Map<?, ?> mp)
                //noinspection unchecked
            {
                updateWeatherParams((Map<String, Object>) mp);
            }

            logger.info("仿真配置加载完成。");
        } catch (Exception e) {
            throw new RuntimeException("配置解析错误: " + e.getMessage(), e);
        }
    }

    /** @return 天气参数当前版本号 */
    public long getWeatherVersion() {
        return weatherVersion.get();
    }

    /**
     * 更新天气参数（带版本号递增）
     *
     * @param newParams 新天气参数
     */
    public synchronized void updateWeatherParams(Map<String, Object> newParams) {
        if (newParams == null) {
            throw new IllegalArgumentException("天气参数映射不能为 null");
        }
        this.weatherParams = newParams;
        weatherVersion.incrementAndGet();
        logger.info("天气参数已运行时更新 (版本 {}): {}", weatherVersion.get(), newParams);
    }

    /**
     * 获取预测参数
     *
     * @return 预测参数映射
     */
    public Map<String, Object> getForecastParams() {
        Object sim = config.get("simulation");
        if (!(sim instanceof Map<?, ?> simMap)) {
            return Map.of();
        }
        Object fc = simMap.get("forecast");
        if (!(fc instanceof Map<?, ?> map)) {
            return Map.of();
        }
        //noinspection unchecked
        return (Map<String, Object>) map;
    }

    /**
     * 获取 tick 间隔 (毫秒)
     *
     * @return tick 间隔
     */
    public int getTickIntervalMillis() {
        Object simulationObj = config.get("simulation");
        if (simulationObj == null) {
            throw new IllegalArgumentException("配置中缺少 'simulation' 键。");
        }
        if (!(simulationObj instanceof Map)) {
            throw new IllegalArgumentException("'simulation' 不是 Map 类型。");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> simulationMap = (Map<String, Object>) simulationObj;

        Object intervalObj = simulationMap.get("tickIntervalMillis");
        if (intervalObj == null) {
            throw new IllegalArgumentException("配置中缺少 'tickIntervalMillis' 键。");
        }
        return (int) intervalObj;
    }

    /**
     * 获取外部电价
     *
     * @return 电价 (默认 9999.0)
     */
    public double getExternalSourceCost() {
        Object simulationObj = config.get("simulation");
        if (simulationObj == null) {
            return 9999.0;
        }
        if (!(simulationObj instanceof Map)) {
            return 9999.0;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> simulationMap = (Map<String, Object>) simulationObj;
        Object costObj = simulationMap.get("externalSourceCost");
        if (costObj == null) {
            return 9999.0;
        }
        return Double.parseDouble(costObj.toString());
    }

    /**
     * 获取外部电网容量上限
     *
     * @return 容量 (kW，默认 9999.0)
     */
    public double getExternalSourceCap() {
        Object simulationObj = config.get("simulation");
        if (simulationObj == null) {
            return 9999.0;
        }
        if (!(simulationObj instanceof Map)) {
            return 9999.0;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> simulationMap = (Map<String, Object>) simulationObj;
        Object capObj = simulationMap.get("externalSourceCap");
        if (capObj == null) {
            return 9999.0;
        }
        return Double.parseDouble(capObj.toString());
    }

    /**
     * 获取每多少个 tick 发送一次聚合指标
     *
     * @return 间隔 tick 数 (默认 2)
     */
    public int getMetricsPerNTicks() {
        Object simulationObj = config.get("simulation");
        if (simulationObj == null) {
            return 2;
        }
        if (!(simulationObj instanceof Map)) {
            return 2;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> simulationMap = (Map<String, Object>) simulationObj;
        Object metricsPerNTick = simulationMap.get("metricsPerNTicks");
        if (metricsPerNTick == null) {
            return 2;
        }
        return (int) metricsPerNTick;
    }

    /**
     * 验证并获取智能体定义列表。
     * 检查每个定义包含有效的 "type" 和 "name" 字段。
     *
     * @return 智能体定义列表
     * @throws IllegalArgumentException 如果配置格式不正确
     */
    public List<Map<String, Object>> getValidatedAgentDefinitions() throws IllegalArgumentException {
        Object simulationObj = config.get("simulation");
        List<Object> rawAgentsList = getAgentsList(simulationObj);
        List<Map<String, Object>> validAgents = new ArrayList<>();

        for (Object agentObj : rawAgentsList) {
            if (!(agentObj instanceof Map)) {
                throw new IllegalArgumentException(
                        "智能体定义不是 Map 类型。实际类型: " + agentObj.getClass().getName());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> agentDef = (Map<String, Object>) agentObj;
            Object typeObj = agentDef.get("type");
            Object nameObj = agentDef.get("name");
            if (!(typeObj instanceof String)) {
                throw new IllegalArgumentException(
                        "智能体 'type' 字段不是 String 类型。");
            }
            if (!(nameObj instanceof String)) {
                throw new IllegalArgumentException(
                        "智能体 'name' 字段不是 String 类型。");
            }
            validAgents.add(agentDef);
        }
        return validAgents;
    }

    /**
     * 按类型和名称查找智能体定义
     *
     * @param expectedType 期望的类型（如 "energySource"）
     * @param agentName    智能体名称
     * @return 智能体定义映射，未找到时返回 null
     */
    public Map<String, Object> findAgentDefinition(String expectedType, String agentName) {
        List<Map<String, Object>> agentsList = this.getValidatedAgentDefinitions();
        for (Map<String, Object> agentDef : agentsList) {
            Object typeObj = agentDef.get("type");
            Object nameObj = agentDef.get("name");
            if (typeObj instanceof String type && nameObj instanceof String name) {
                if (expectedType.equalsIgnoreCase(type) && agentName.equals(name)) {
                    return agentDef;
                }
            } else {
                logger.error("智能体定义中 'type' 或 'name' 字段类型异常: {}", agentDef);
            }
        }
        return null;
    }

    /**
     * 从仿真对象中提取智能体列表
     */
    private static List<Object> getAgentsList(Object simulationObj) {
        if (simulationObj == null) {
            throw new IllegalArgumentException("配置中缺少 'simulation' 键。");
        }
        if (!(simulationObj instanceof Map)) {
            throw new IllegalArgumentException("'simulation' 不是 Map 类型。");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> simulationMap = (Map<String, Object>) simulationObj;

        Object agentsObj = simulationMap.get("agents");
        if (agentsObj == null) {
            throw new IllegalArgumentException("配置中缺少 'agents' 键。");
        }
        if (!(agentsObj instanceof List)) {
            throw new IllegalArgumentException("'agents' 不是 List 类型。");
        }
        @SuppressWarnings("unchecked")
        List<Object> rawAgentsList = (List<Object>) agentsObj;
        return rawAgentsList;
    }
}