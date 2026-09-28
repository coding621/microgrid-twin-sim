package com.microgrid.sim.simulation;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.service.EventControlService;
import com.microgrid.sim.service.LogAggregatorService;
import com.microgrid.sim.service.WeatherService;
import com.microgrid.sim.simulation.base.AbstractEnergySourceEntity;

/**
 * 光伏发电源服务
 *
 * <p>基于光伏组件模型计算发电量：
 * <pre>
 *   P = N × η × A × G × [1 + β × (T_cell - T_ref)]
 *   T_cell = T_amb + G × (NOCT - 20) / 800
 * </pre>
 *
 *
 * @author Coding
 */
public class EnergySourceService extends AbstractEnergySourceEntity {

    /** 天气服务 */
    private final WeatherService weather;
    /** 光伏板数量 */
    private final double noOfPanels;
    /** 组件效率 */
    private final double efficiency;
    /** 组件面积 (m²) */
    private final double area;
    /** 温度系数 (/°C) */
    private final double tempCoeff;
    /** 额定工作电池温度 NOCT (°C) */
    private final double noct;

    /**
     * 构造函数
     *
     * @param name       实体名称
     * @param logger     日志服务
     * @param registry   状态注册中心
     * @param events     事件控制服务
     * @param weather    天气服务
     * @param noOfPanels 光伏板数量
     * @param efficiency 组件效率 (0-1)
     * @param area       组件面积 (m²)
     * @param tempCoeff  温度系数 (/°C)
     * @param noct       额定工作电池温度 (°C)
     */
    public EnergySourceService(String name, LogAggregatorService logger,
                               AgentStateRegistry registry, EventControlService events,
                               WeatherService weather, double noOfPanels, double efficiency,
                               double area, double tempCoeff, double noct) {
        super(name, logger, registry, events, noOfPanels * efficiency * area * 1000.0);
        this.weather = weather;
        this.noOfPanels = noOfPanels;
        this.efficiency = efficiency;
        this.area = area;
        this.tempCoeff = tempCoeff;
        this.noct = noct;
    }

    /**
     * 每个 tick 的行为：计算光伏发电量
     */
    @Override
    public void onTick(long tick) {
        if (!isBroken()) {
            double G = weather.getIrradiance(tick);
            double T = weather.getTemperature(tick, G);
            currentProduction = calculateProduction(G, T);
        } else {
            currentProduction = 0.0;
        }
    }

    /**
     * 基于光伏组件模型计算发电量
     *
     * @param G 辐照度 (W/m²)
     * @param T 环境温度 (°C)
     * @return 发电量 (kW)
     */
    @Override
    protected double calculateProduction(double G, double T) {
        // 电池温度
        double Tcell = T + G * (noct - 20.0) / 800.0;
        // 功率 = N × η × A × G × [1 + β × (Tcell - Tref)]
        double power = noOfPanels * efficiency * area * G
                * (1.0 + tempCoeff * (Tcell - 25.0));
        return Math.max(0.0, power / 1000.0); // 转换为 kW
    }
}