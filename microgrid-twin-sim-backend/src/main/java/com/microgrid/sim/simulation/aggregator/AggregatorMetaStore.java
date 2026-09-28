package com.microgrid.sim.simulation.aggregator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聚合器元数据存储 - 保存在仿真启动时一次性下发的电池静态配置。
 *
 * <p>聚合器在进行确定性规划时需要使用这些电池参数（容量、充放电效率、C-rate等）。
 *
 * @author Coding
 */
public final class AggregatorMetaStore {

    /**
     * 电池元数据记录 - 描述单个电池的静态物理参数。
     *
     * @param capacity 电池容量 (kWh)
     * @param etaC     充电效率 (0-1)
     * @param etaD     放电效率 (0-1)
     * @param cRate    充放电倍率 (h⁻¹)
     */
    public record BatteryMeta(double capacity, double etaC, double etaD,
                              double cRate) {
    }

    /** 电池名称到元数据的映射 */
    private final Map<String, BatteryMeta> batteries = new ConcurrentHashMap<>();

    /**
     * 添加电池元数据
     *
     * @param name 电池名称（如 "Battery1"）
     * @param m    电池元数据
     */
    public void addBattery(String name, BatteryMeta m) {
        batteries.put(name, m);
    }

    /**
     * 获取指定电池的元数据
     *
     * @param name 电池名称
     * @return 电池元数据，不存在时返回 null
     */
    public BatteryMeta getBattery(String name) {
        return batteries.get(name);
    }

    /**
     * 获取所有电池的元数据映射
     *
     * @return 不可修改的映射
     */
    public Map<String, BatteryMeta> allBatteries() {
        return batteries;
    }
}