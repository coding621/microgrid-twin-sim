package com.microgrid.sim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 微电网数字孪生系统 - Spring Boot 主启动类
 * 
 * <p>本系统基于 Spring Boot 架构实现微电网仿真。
 * 核心功能包括：时序仿真、光伏发电模型、电池储能充放电、建筑负载模拟、
 * 天气预报生成、概率预测、场景生成、确定性线性规划调度等。
 *
 * @author Coding
 */
@SpringBootApplication
public class EnergyTwinBootApplication {

    /**
     * Spring Boot 应用入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(EnergyTwinBootApplication.class, args);
    }
}