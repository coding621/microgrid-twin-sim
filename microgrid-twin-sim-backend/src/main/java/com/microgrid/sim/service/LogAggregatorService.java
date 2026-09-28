package com.microgrid.sim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 日志聚合服务 - 在内存中按智能体名称分组聚合仿真日志。
 *
 * <p>所有仿真实体通过此服务记录日志，前端可通过 REST API 查询分组日志。
 *
 * @author Coding
 */
@Service
public class LogAggregatorService {

    private static final Logger logger = LoggerFactory.getLogger(LogAggregatorService.class);

    /** 按智能体名称分组的日志存储 */
    private final Map<String, List<String>> logsByAgent = new ConcurrentHashMap<>();

    /**
     * 为指定智能体添加一条日志消息
     *
     * @param agentName 智能体名称（如 "Battery1", "AggregatorService"）
     * @param message   日志消息内容
     */
    public void log(String agentName, String message) {
        String timestampedMessage = LocalDateTime.now() + ": " + message;
        logsByAgent
                .computeIfAbsent(agentName, k -> new CopyOnWriteArrayList<>())
                .add(timestampedMessage);
        // 同时输出到 SLF4J
        logger.info("[{}] {}", agentName, message);
    }

    /**
     * 获取所有智能体的日志
     *
     * @return 不可修改的日志映射
     */
    public Map<String, List<String>> getAllLogs() {
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : logsByAgent.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 获取指定智能体的日志
     *
     * @param agentName 智能体名称
     * @return 不可修改的日志列表，不存在时返回空列表
     */
    public List<String> getLogsForAgent(String agentName) {
        return Collections.unmodifiableList(
                logsByAgent.getOrDefault(agentName, Collections.emptyList()));
    }

    /** 清空所有日志（用于重启仿真） */
    public void clearLogs() {
        logsByAgent.clear();
        logger.info("所有仿真日志已清空");
    }
}