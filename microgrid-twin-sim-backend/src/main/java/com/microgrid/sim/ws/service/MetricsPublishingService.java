package com.microgrid.sim.ws.service;

import com.microgrid.sim.ws.dto.MetricsMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 指标消息发布服务 - 通过 STOMP WebSocket 向前端发布聚合指标数据。
 *
 * @author Coding
 */
@Service
public class MetricsPublishingService {

    /** STOMP 消息模板 */
    private final SimpMessagingTemplate template;

    /**
     * 构造函数注入
     *
     * @param template STOMP 消息模板
     */
    public MetricsPublishingService(SimpMessagingTemplate template) {
        this.template = template;
    }

    /**
     * 发布指标消息到 /topic/metrics
     *
     * @param message 指标消息对象
     */
    public void publish(MetricsMessage message) {
        template.convertAndSend("/topic/metrics", message);
    }
}