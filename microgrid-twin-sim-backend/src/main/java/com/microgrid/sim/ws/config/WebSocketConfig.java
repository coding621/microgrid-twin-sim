package com.microgrid.sim.ws.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置 - 启用 STOMP 消息代理并注册端点。
 *
 *
 * @author Coding
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置消息代理
     *
     * @param registry 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单内存消息代理，前缀 /topic
        registry.enableSimpleBroker("/topic");
        // 客户端发往服务器的目的地前缀
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * 注册 STOMP 端点
     *
     * @param registry STOMP 端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 数据流端点，支持跨域和 SockJS 回退
        registry.addEndpoint("/ws/data")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}