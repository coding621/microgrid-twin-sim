package com.microgrid.sim.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 天气服务。
 *
 * <p>基于简化天气模型生成模拟的日照辐照度和温度数据。
 * 辐照度: 日出至日落间正弦曲线模拟，带随机噪声。
 * 温度: 均值周期函数加噪声，受日照影响。
 *
 *
 * @author Coding
 */
@Service
public class WeatherService {

    /** 仿真配置服务 */
    private final SimulationConfigService configService;

    /**
     * 构造函数注入
     *
     * @param configService 仿真配置服务
     */
    public WeatherService(SimulationConfigService configService) {
        this.configService = configService;
    }

    /**
     * 获取当前辐照度 (W/m²) - 基于正弦曲线模型
     *
     * @param tick 当前 tick 序号
     * @return 辐照度 (W/m²)
     */
    public double getIrradiance(long tick) {
        Map<String, Object> w = configService.getWeatherParams();
        int rise = safeInt(w, "sunriseTick", 6);
        int set = safeInt(w, "sunsetTick", 18);
        int peak = safeInt(w, "sunPeakTick", 12);
        double gPeak = safeDouble(w, "gPeak", 1000.0);
        double sigmaG = safeDouble(w, "sigmaG", 0.15);

        // 以 24 为周期模拟昼夜循环：每个周期内 sunriseTick~sunsetTick 之间有日照
        long dayTick = tick % 24;
        if (dayTick < rise || dayTick > set) {
            return 0.0;
        }

        // 正弦曲线辐照度模型
        double G = gPeak * Math.sin(Math.PI * (dayTick - rise) / (set - rise));
        // 加高斯随机噪声
        G += sigmaG * gPeak * Math.random();
        if (G < 0) {
            G = 0.0;
        }
        return G;
    }

    /**
     * 获取当前温度 (°C) - 基于日周期模型
     *
     * @param tick 当前 tick 序号
     * @param G    当前辐照度 (W/m²)
     * @return 温度 (°C)
     */
    public double getTemperature(long tick, double G) {
        Map<String, Object> w = configService.getWeatherParams();
        int rise = safeInt(w, "sunriseTick", 6);
        int set = safeInt(w, "sunsetTick", 18);
        int tMinTick = safeInt(w, "tempMinTick", 5);
        double Tday = safeDouble(w, "tempMeanDay", 25.0);
        double Tnight = safeDouble(w, "tempMeanNight", 15.0);
        double sigmaT = safeDouble(w, "sigmaT", 0.8);

        // 基础温度日周期
        double T = Tnight + (Tday - Tnight) * Math.sin(Math.PI * (tick - rise) / (set - rise));
        if (tick < rise) {
            T = Tnight - (Tday - Tnight) * Math.sin(Math.PI * (tick - tMinTick) / (rise - tMinTick + 1));
        }

        // 辐照度对温度的加热效应
        T += G * 0.02;

        // 添加随机噪声
        T += sigmaT * Math.random();
        return T;
    }

    /** 安全地从 Map 获取 int 值，缺失时返回默认值 */
    private static int safeInt(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    /** 安全地从 Map 获取 double 值，缺失时返回默认值 */
    private static double safeDouble(Map<String, Object> map, String key, double defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return defaultValue;
    }
}