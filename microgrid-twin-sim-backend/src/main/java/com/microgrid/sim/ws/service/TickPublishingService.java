package com.microgrid.sim.ws.service;

import com.microgrid.sim.ws.dto.TickDataMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Tick 数据发布服务 - 通过 STOMP WebSocket 向前端发布每个 tick 的实时状态数据。
 *
 * @author Coding
 */
@Service
public class TickPublishingService {

    /** STOMP 消息模板 */
    private final SimpMessagingTemplate template;

    /**
     * 构造函数注入
     *
     * @param template STOMP 消息模板
     */
    public TickPublishingService(SimpMessagingTemplate template) {
        this.template = template;
    }

    /**
     * 发布 tick 数据到 /topic/tickData
     *
     * @param message tick 数据消息对象
     */
    public void publish(TickDataMessage message) {
        template.convertAndSend("/topic/tickData", message);
    }
}