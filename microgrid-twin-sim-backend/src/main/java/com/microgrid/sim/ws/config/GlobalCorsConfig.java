package com.microgrid.sim.ws.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局 CORS 跨域配置 - 允许前端跨域访问后端 REST API。
 *
 * <p>解决浏览器 "strict-origin-when-cross-origin 403 Forbidden" 错误。
 *
 * @author Coding
 */
@Configuration
public class GlobalCorsConfig {

    /**
     * 配置全局 CORS 规则 - 允许所有来源、所有方法和所有请求头。
     * 同时允许携带凭证（Credentials），并处理 OPTIONS 预检请求。
     *
     * @return WebMvcConfigurer 实例
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")               // 所有路径
                        .allowedOriginPatterns("*")      // 允许所有来源
                        .allowedMethods("*")             // 所有方法
                        .allowedHeaders("*")             // 所有请求头
                        .maxAge(3600);                   // 预检请求缓存1小时
            }
        };
    }
}