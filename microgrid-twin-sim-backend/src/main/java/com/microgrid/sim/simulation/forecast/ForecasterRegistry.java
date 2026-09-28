package com.microgrid.sim.simulation.forecast;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 预测器注册表 — 通过方法名动态创建预测器实例。
 *
 * <p>使用方式：
 * <pre>
 *   Forecaster f = ForecasterRegistry.create("random_forest", 24);
 *   Forecaster f = ForecasterRegistry.create("ensemble", 24);
 * </pre>
 *
 * <p>新增算法只需在此注册，AggregatorService 无需修改。
 *
 * @author Coding
 */
public final class ForecasterRegistry {

    /** 注册的预测器工厂，按名称映射 */
    private static final Map<String, Function<Integer, Forecaster>> FACTORIES = new LinkedHashMap<>();

    static {
        FACTORIES.put("random_forest", RandomForestForecaster::new);
        FACTORIES.put("statistical",  StatisticalForecaster::new);
        FACTORIES.put("similar_day",  SimilarDayForecaster::new);
        FACTORIES.put("ensemble",     EnsembleForecaster::new);
    }

    private ForecasterRegistry() {
    }

    /**
     * 根据方法名创建预测器实例。
     *
     * @param method  算法名称，如 "random_forest"、"statistical"、"ensemble"
     * @param horizon 预测视野（步数）
     * @return 预测器实例
     * @throws IllegalArgumentException 如果方法名未知
     */
    public static Forecaster create(String method, int horizon) {
        Function<Integer, Forecaster> factory = FACTORIES.get(method);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "未知的预测方法: \"" + method + "\"，可选: " + availableMethods());
        }
        return factory.apply(horizon);
    }

    /**
     * 列出所有已注册的算法名称。
     *
     * @return 算法名称列表
     */
    public static String[] availableMethods() {
        return FACTORIES.keySet().toArray(new String[0]);
    }

    /**
     * 默认算法名称。
     */
    public static String defaultMethod() {
        return "random_forest";
    }
}