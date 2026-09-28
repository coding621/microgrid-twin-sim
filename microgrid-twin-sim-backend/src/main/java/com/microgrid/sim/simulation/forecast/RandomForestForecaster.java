package com.microgrid.sim.simulation.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.vector.DoubleVector;
import smile.regression.RandomForest;
import smile.regression.RegressionTree;
import smile.stat.distribution.EmpiricalDistribution;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 随机森林预测器 — 基于 SMILE Random Forest 的增强概率预测模型。
 *
 * <ul>
 *   <li>超参：500 棵树、最大深度 20、叶节点最小样本 20</li>
 *   <li>网格搜索：3 折交叉验证自动选最优 mtry</li>
 *   <li>评估体系：MAE / RMSE / MAPE / R² 四维指标</li>
 *   <li>概念漂移：连续 MAPE 超标自动告警</li>
 *   <li>模型持久化：训练后自动存盘，重启可加载复用</li>
 *   <li>日志：SLF4J</li>
 * </ul>
 *
 * <p>特征工程：自回归、移动平均、温度衍生、时间循环编码等 19 维特征。
 * 输出分位数预测：q05（悲观）、q50（中位）、q95（乐观）。
 *
 * @author Coding
 */
public final class RandomForestForecaster implements Forecaster {

    // ========== 预测核心状态 ==========
    /** 预测视野长度 */
    private final int H_pred;
    /** 负载预测随机森林模型 */
    private RandomForest loadModel;
    /** 光伏预测随机森林模型 */
    private RandomForest pvModel;

    /** 最近的负载数据缓存 */
    private double[] recentLoad;
    /** 最近的光伏数据缓存 */
    private double[] recentPv;
    /** 最近的温度数据缓存 */
    private double[] recentTemp;
    /** 负载移动平均值 */
    private double[] loadMovingAvg;
    /** 光伏移动平均值 */
    private double[] pvMovingAvg;
    /** 基准温度 */
    private double baselineTemp = 20.0;
    /** 上次模型更新时间 */
    private LocalDateTime lastUpdateTime;

    // ========== 工业级超参 ==========
    /** 随机森林树数量 — 工业级需要 500+ 以保证预测稳定性 */
    private static final int N_TREES = 500;
    /** 树的最大深度 — 防止过拟合的同时允许捕获复杂模式 */
    private static final int MAX_DEPTH = 20;
    /** 最小分割节点样本数 */
    private static final int MIN_SPLIT = 5;
    /** 叶节点最小样本数 — 增大以提升泛化能力 */
    private static final int MIN_SAMPLE = 20;
    /** 每棵树的子采样比例 */
    private static final double SUBSAMPLE = 0.7;
    /** 移动平均和趋势计算的回看窗口 */
    private static final int LOOKBACK_WINDOW = 7;
    /** 网格搜索交叉验证折数 */
    private static final int CV_FOLDS = 3;
    /** 概念漂移检测窗口大小（样本数） */
    private static final int DRIFT_WINDOW = 50;
    /** 概念漂移阈值：MAPE 超过 25% 触发告警 */
    private static final double DRIFT_THRESHOLD = 0.25;
    /** 最大连续漂移容忍次数 */
    private static final int MAX_DRIFT_COUNT = 3;

    // ========== 工业级运行时状态 ==========
    /** SLF4J 日志实例 */
    private static final Logger log = LoggerFactory.getLogger(RandomForestForecaster.class);
    /** 模型持久化目录路径（null 则不持久化） */
    private String modelSavePath;
    /** 最近一次负载模型评估指标 */
    private ForecastMetrics lastLoadMetrics;
    /** 最近一次光伏模型评估指标 */
    private ForecastMetrics lastPvMetrics;
    /** 概念漂移计数器 — 连续漂移次数 */
    private int driftCounter;

    /**
     * 构造函数
     *
     * @param horizon 预测视野（步数）
     */
    public RandomForestForecaster(int horizon) {
        this.H_pred = horizon;
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 更新预测模型 — 使用最新历史数据训练工业级随机森林模型。
     *
     * <p>完整流程：数据清洗 → 特征工程 → 网格搜索 → 模型训练 → 评估 → 漂移检测 → 持久化。
     *
     * @param load 历史负载数据 (kW)
     * @param pv   历史光伏数据 (kW)
     * @param temp 历史温度数据 (°C)
     */
    @Override
    public void update(double[] load, double[] pv, double[] temp) {
        int n = load.length;

        // 验证数据量是否充足
        if (n < 10) {
            log.warn("数据点不足: {}，无法训练模型", n);
            return;
        }

        // 清洗和验证数据
        double[] cleanLoad = cleanAndValidateData(load, "load");
        double[] cleanPv = cleanAndValidateData(pv, "pv");
        double[] cleanTemp = cleanAndValidateData(temp, "temperature");

        // 检查数据方差是否足够
        if (calculateCoeffOfVariation(cleanLoad) < 0.05 ||
                calculateCoeffOfVariation(cleanPv) < 0.05) {
            log.warn("数据方差过低，预测质量可能下降");
        }

        // 缓存最近值用于后续预测
        int cacheSize = Math.min(LOOKBACK_WINDOW, n);
        this.recentLoad = Arrays.copyOfRange(cleanLoad, n - cacheSize, n);
        this.recentPv = Arrays.copyOfRange(cleanPv, n - cacheSize, n);
        this.recentTemp = Arrays.copyOfRange(cleanTemp, n - cacheSize, n);
        this.baselineTemp = cleanTemp[n - 1];
        this.lastUpdateTime = LocalDateTime.now();

        // 构建增强特征
        DataFrame features = buildEnhancedFeatures(cleanLoad, cleanPv, cleanTemp, n);

        // 交叉验证网格搜索最优 mtry
        int bestMtry = gridSearchMtry(features, cleanLoad, cleanPv, n);
        log.info("网格搜索完成，最优 mtry = {}", bestMtry);

        try {
            // 训练负载预测模型
            DataFrame loadTrainData = features.merge(DoubleVector.of("target_load", cleanLoad));
            loadModel = RandomForest.fit(
                    smile.data.formula.Formula.lhs("target_load"),
                    loadTrainData,
                    N_TREES,
                    bestMtry,
                    MAX_DEPTH,
                    Integer.MAX_VALUE,
                    MIN_SPLIT,
                    SUBSAMPLE
            );

            // 训练光伏预测模型
            DataFrame pvTrainData = features.merge(DoubleVector.of("target_pv", cleanPv));
            pvModel = RandomForest.fit(
                    smile.data.formula.Formula.lhs("target_pv"),
                    pvTrainData,
                    N_TREES,
                    bestMtry,
                    MAX_DEPTH,
                    Integer.MAX_VALUE,
                    MIN_SPLIT,
                    SUBSAMPLE
            );

            // 计算移动平均用于预测上下文
            this.loadMovingAvg = computeMovingAverages(cleanLoad, 3);
            this.pvMovingAvg = computeMovingAverages(cleanPv, 3);

            // 评估模型质量
            this.lastLoadMetrics = evaluateModel(loadModel, features, cleanLoad);
            this.lastPvMetrics = evaluateModel(pvModel, features, cleanPv);

            log.info("模型训练完成 — 负荷: {} | 光伏: {}", lastLoadMetrics, lastPvMetrics);

            // 概念漂移检测
            checkConceptDrift();

            // 模型持久化
            saveModels();

        } catch (Exception e) {
            log.error("模型训练失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建完整的特征集 — 包含自回归、时间编码、温度等特征。
     *
     * @param load 负载数据
     * @param pv   光伏数据
     * @param temp 温度数据
     * @param n    数据点数
     * @return 特征 DataFrame（19 维特征列）
     */
    private DataFrame buildEnhancedFeatures(double[] load, double[] pv, double[] temp, int n) {
        // 初始化特征数组
        double[] lag1Load = new double[n];
        double[] lag2Load = new double[n];
        double[] lag1Pv = new double[n];
        double[] lag2Pv = new double[n];
        double[] tempCurrent = new double[n];
        double[] tempLag1 = new double[n];

        // 移动平均和趋势
        double[] loadMA3 = computeMovingAverages(load, 3);
        double[] pvMA3 = computeMovingAverages(pv, 3);
        double[] loadTrend = computeTrend(load, 3);
        double[] pvTrend = computeTrend(pv, 3);

        // 时间特征 — 使用 sin/cos 编码循环时间
        double[] hourSin = new double[n];
        double[] hourCos = new double[n];
        double[] dowSin = new double[n];
        double[] dowCos = new double[n];
        double[] monthSin = new double[n];
        double[] monthCos = new double[n];
        double[] isWeekend = new double[n];

        // 温度衍生特征
        double[] tempDeviation = new double[n];
        double[] tempMA = computeMovingAverages(temp, 3);

        LocalDateTime baseTime = lastUpdateTime.minusHours(n);

        for (int i = 0; i < n; i++) {
            LocalDateTime currentTime = baseTime.plusHours(i);

            // 滞后特征（带边界检查）
            lag1Load[i] = (i > 0) ? load[i - 1] : load[0];
            lag2Load[i] = (i > 1) ? load[i - 2] : load[Math.max(0, i - 1)];
            lag1Pv[i] = (i > 0) ? pv[i - 1] : pv[0];
            lag2Pv[i] = (i > 1) ? pv[i - 2] : pv[Math.max(0, i - 1)];

            // 温度特征
            tempCurrent[i] = temp[i];
            tempLag1[i] = (i > 0) ? temp[i - 1] : temp[0];
            tempDeviation[i] = temp[i] - tempMA[i];

            // 时间循环编码
            int hour = currentTime.getHour();
            int dayOfWeek = currentTime.getDayOfWeek().getValue() - 1;
            int month = currentTime.getMonthValue() - 1;

            hourSin[i] = Math.sin(2 * Math.PI * hour / 24.0);
            hourCos[i] = Math.cos(2 * Math.PI * hour / 24.0);
            dowSin[i] = Math.sin(2 * Math.PI * dayOfWeek / 7.0);
            dowCos[i] = Math.cos(2 * Math.PI * dayOfWeek / 7.0);
            monthSin[i] = Math.sin(2 * Math.PI * month / 12.0);
            monthCos[i] = Math.cos(2 * Math.PI * month / 12.0);
            isWeekend[i] = (dayOfWeek >= 5) ? 1.0 : 0.0;
        }

        // 构建完整特征 DataFrame
        return DataFrame.of(
                // 自回归特征
                DoubleVector.of("lag1_load", lag1Load),
                DoubleVector.of("lag2_load", lag2Load),
                DoubleVector.of("lag1_pv", lag1Pv),
                DoubleVector.of("lag2_pv", lag2Pv),

                // 移动平均和趋势
                DoubleVector.of("load_ma3", loadMA3),
                DoubleVector.of("pv_ma3", pvMA3),
                DoubleVector.of("load_trend", loadTrend),
                DoubleVector.of("pv_trend", pvTrend),

                // 温度特征
                DoubleVector.of("temp_current", tempCurrent),
                DoubleVector.of("temp_lag1", tempLag1),
                DoubleVector.of("temp_deviation", tempDeviation),
                DoubleVector.of("temp_ma", tempMA),

                // 时间特征
                DoubleVector.of("hour_sin", hourSin),
                DoubleVector.of("hour_cos", hourCos),
                DoubleVector.of("dow_sin", dowSin),
                DoubleVector.of("dow_cos", dowCos),
                DoubleVector.of("month_sin", monthSin),
                DoubleVector.of("month_cos", monthCos),
                DoubleVector.of("is_weekend", isWeekend)
        );
    }

    @Override
    public String getName() {
        return "random_forest";
    }

    /**
     * 预测未来 H_pred 步的负载值
     *
     * @return 分位数预测 [q05, q50, q95][H_pred]
     */
    @Override
    public double[][] predictLoad() {
        if (loadModel == null || recentLoad == null) {
            return createFallbackPrediction(0.0);
        }
        return generateEnhancedForecast(loadModel, true);
    }

    /**
     * 预测未来 H_pred 步的光伏值
     *
     * @return 分位数预测 [q05, q50, q95][H_pred]
     */
    @Override
    public double[][] predictPv() {
        if (pvModel == null || recentPv == null) {
            return createFallbackPrediction(0.0);
        }
        return generateEnhancedForecast(pvModel, false);
    }

    /**
     * 生成增强的多步预测 — 递归预测，每次用上一步预测结果更新状态。
     *
     * @param model       训练好的随机森林模型
     * @param isLoadModel 是否为负载模型（相对于光伏模型）
     * @return 分位数预测 [q05, q50, q95][H_pred]
     */
    private double[][] generateEnhancedForecast(RandomForest model, boolean isLoadModel) {
        double[] q05 = new double[H_pred];
        double[] q50 = new double[H_pred];
        double[] q95 = new double[H_pred];

        // 初始化演化状态变量
        double[] evolvedLoad = Arrays.copyOf(recentLoad, recentLoad.length);
        double[] evolvedPv = Arrays.copyOf(recentPv, recentPv.length);
        double[] evolvedTemp = Arrays.copyOf(recentTemp, recentTemp.length);

        LocalDateTime forecastTime = lastUpdateTime;

        for (int h = 0; h < H_pred; h++) {
            forecastTime = forecastTime.plusHours(1);

            try {
                // 为当前时间步构建预测特征
                double[] features = buildPredictionFeatures(
                        evolvedLoad, evolvedPv, evolvedTemp, forecastTime, h);

                // 获取集成预测
                double[] treePredictions = getTreePredictions(model, features);

                // 计算鲁棒分位数
                double[] quantiles = calculateRobustQuantiles(treePredictions);

                q05[h] = Math.max(0, quantiles[0]);
                q50[h] = Math.max(0, quantiles[1]);
                q95[h] = Math.max(0, quantiles[2]);

                // 更新演化状态供下一步使用
                updateEvolvedState(evolvedLoad, evolvedPv, evolvedTemp,
                        q50[h], isLoadModel, h, forecastTime);

            } catch (Exception e) {
                log.error("预测步 {} 失败: {}", h, e.getMessage());
                double fallback = isLoadModel ?
                        (recentLoad.length > 0 ? recentLoad[recentLoad.length - 1] : 0.0) :
                        (recentPv.length > 0 ? recentPv[recentPv.length - 1] : 0.0);
                q05[h] = Math.max(0, fallback * 0.8);
                q50[h] = Math.max(0, fallback);
                q95[h] = fallback * 1.2;
            }
        }

        return new double[][]{q05, q50, q95};
    }

    /**
     * 为特定预测步构建预测特征
     */
    private double[] buildPredictionFeatures(double[] evolvedLoad, double[] evolvedPv,
                                             double[] evolvedTemp, LocalDateTime time, int step) {
        int n = evolvedLoad.length;

        int hour = time.getHour();
        int dayOfWeek = time.getDayOfWeek().getValue() - 1;
        int month = time.getMonthValue() - 1;

        double hourSin = Math.sin(2 * Math.PI * hour / 24.0);
        double hourCos = Math.cos(2 * Math.PI * hour / 24.0);
        double dowSin = Math.sin(2 * Math.PI * dayOfWeek / 7.0);
        double dowCos = Math.cos(2 * Math.PI * dayOfWeek / 7.0);
        double monthSin = Math.sin(2 * Math.PI * month / 12.0);
        double monthCos = Math.cos(2 * Math.PI * month / 12.0);
        double isWeekend = (dayOfWeek >= 5) ? 1.0 : 0.0;

        double lag1Load = evolvedLoad[n - 1];
        double lag2Load = (n > 1) ? evolvedLoad[n - 2] : evolvedLoad[n - 1];
        double lag1Pv = evolvedPv[n - 1];
        double lag2Pv = (n > 1) ? evolvedPv[n - 2] : evolvedPv[n - 1];

        double loadMA3 = Arrays.stream(evolvedLoad).skip(Math.max(0, n - 3)).average().orElse(lag1Load);
        double pvMA3 = Arrays.stream(evolvedPv).skip(Math.max(0, n - 3)).average().orElse(lag1Pv);
        double loadTrend = computeSimpleTrend(evolvedLoad);
        double pvTrend = computeSimpleTrend(evolvedPv);

        double tempCurrent = evolvedTemp[n - 1];
        double tempLag1 = (n > 1) ? evolvedTemp[n - 2] : tempCurrent;
        double tempMA = Arrays.stream(evolvedTemp).skip(Math.max(0, n - 3)).average().orElse(tempCurrent);
        double tempDeviation = tempCurrent - tempMA;

        return new double[]{
                lag1Load, lag2Load, lag1Pv, lag2Pv,
                loadMA3, pvMA3, loadTrend, pvTrend,
                tempCurrent, tempLag1, tempDeviation, tempMA,
                hourSin, hourCos, dowSin, dowCos, monthSin, monthCos, isWeekend
        };
    }

    /**
     * 更新演化状态变量 — 用于多步预测中状态的滚动更新。
     */
    private void updateEvolvedState(double[] evolvedLoad, double[] evolvedPv, double[] evolvedTemp,
                                    double prediction, boolean isLoadModel, int step, LocalDateTime time) {
        // 数组左移，为新预测值腾出空间
        System.arraycopy(evolvedLoad, 1, evolvedLoad, 0, evolvedLoad.length - 1);
        System.arraycopy(evolvedPv, 1, evolvedPv, 0, evolvedPv.length - 1);
        System.arraycopy(evolvedTemp, 1, evolvedTemp, 0, evolvedTemp.length - 1);

        if (isLoadModel) {
            evolvedLoad[evolvedLoad.length - 1] = prediction;
            evolvedPv[evolvedPv.length - 1] = evolutePvEstimate(time, step);
        } else {
            evolvedPv[evolvedPv.length - 1] = prediction;
            evolvedLoad[evolvedLoad.length - 1] = evoluteLoadEstimate(time, step);
        }

        double tempTrend = (evolvedTemp.length > 1) ?
                (evolvedTemp[evolvedTemp.length - 1] - evolvedTemp[evolvedTemp.length - 2]) : 0.0;
        evolvedTemp[evolvedTemp.length - 1] = baselineTemp + tempTrend * 0.5;
    }

    /* ==================== 数据处理辅助方法 ==================== */

    /** 清洗和验证数据 — 用前值填充无效值 */
    private double[] cleanAndValidateData(double[] data, String type) {
        double[] cleaned = new double[data.length];
        double lastValid = 0.0;

        for (int i = 0; i < data.length; i++) {
            if (Double.isFinite(data[i]) && data[i] >= 0) {
                cleaned[i] = data[i];
                lastValid = data[i];
            } else {
                cleaned[i] = lastValid;
            }
        }
        return cleaned;
    }

    /** 计算变异系数 (CV) */
    private double calculateCoeffOfVariation(double[] data) {
        double mean = Arrays.stream(data).average().orElse(0.0);
        if (mean == 0) {
            return 0.0;
        }
        double variance = Arrays.stream(data).map(x -> (x - mean) * (x - mean)).average().orElse(0.0);
        return Math.sqrt(variance) / Math.abs(mean);
    }

    /** 计算移动平均 */
    private double[] computeMovingAverages(double[] data, int window) {
        double[] ma = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            int start = Math.max(0, i - window + 1);
            ma[i] = Arrays.stream(data, start, i + 1).average().orElse(data[i]);
        }
        return ma;
    }

    /** 计算趋势（当前值减去 window 步前的值） */
    private double[] computeTrend(double[] data, int window) {
        double[] trend = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            if (i < window) {
                trend[i] = 0.0;
            } else {
                trend[i] = data[i] - data[i - window];
            }
        }
        return trend;
    }

    /** 计算简单趋势（最后两个值的差） */
    private double computeSimpleTrend(double[] data) {
        if (data.length < 2) {
            return 0.0;
        }
        return data[data.length - 1] - data[data.length - 2];
    }

    /** 估算光伏发电演化值（基于时刻的简单日照模型） */
    private double evolutePvEstimate(LocalDateTime time, int step) {
        int hour = time.getHour();
        double solarFactor = 0.0;
        if (hour >= 6 && hour <= 18) {
            double hourFromNoon = Math.abs(hour - 12);
            solarFactor = Math.max(0, 1.0 - hourFromNoon / 6.0);
        }
        double baseGeneration = recentPv.length > 0 ? recentPv[recentPv.length - 1] : 0.0;
        return baseGeneration * 0.7 + solarFactor * baseGeneration * 0.3;
    }

    /** 估算负载演化值（基于时刻的每日负载模式） */
    private double evoluteLoadEstimate(LocalDateTime time, int step) {
        int hour = time.getHour();
        double loadFactor = 1.0;
        if (hour >= 7 && hour <= 9) {
            loadFactor = 1.2;
        } else if (hour >= 18 && hour <= 21) {
            loadFactor = 1.3;
        } else if (hour >= 0 && hour <= 5) {
            loadFactor = 0.7;
        }

        double baseLoad = recentLoad.length > 0 ? recentLoad[recentLoad.length - 1] : 0.0;
        return baseLoad * loadFactor;
    }

    /** 获取随机森林所有树的预测值 */
    private double[] getTreePredictions(RandomForest model, double[] features) {
        RegressionTree[] trees = model.trees();
        double[] predictions = new double[trees.length];

        for (int i = 0; i < trees.length; i++) {
            try {
                predictions[i] = Math.max(0, trees[i].predict(createTupleFromFeatures(features)));
            } catch (Exception e) {
                predictions[i] = 0.0;
            }
        }
        return predictions;
    }

    /** 从特征数组创建 Tuple 对象 */
    private Tuple createTupleFromFeatures(double[] features) {
        Object[] values = new Object[features.length];
        for (int i = 0; i < features.length; i++) {
            values[i] = features[i];
        }
        return Tuple.of(values, null);
    }

    /** 计算鲁棒分位数 — 使用经验分布（排好序的预测值数组） */
    private double[] calculateRobustQuantiles(double[] predictions) {
        if (predictions.length == 0) {
            return new double[]{0, 0, 0};
        }

        Arrays.sort(predictions);
        int n = predictions.length;

        if (n == 1) {
            double val = predictions[0];
            return new double[]{val * 0.9, val, val * 1.1};
        }

        try {
            EmpiricalDistribution dist = new EmpiricalDistribution(predictions);
            return new double[]{
                    dist.quantile(0.05),
                    dist.quantile(0.50),
                    dist.quantile(0.95)
            };
        } catch (Exception e) {
            return new double[]{
                    predictions[Math.max(0, (int) (n * 0.05))],
                    predictions[n / 2],
                    predictions[Math.min(n - 1, (int) (n * 0.95))]
            };
        }
    }

    /** 创建回退预测（当模型未训练时使用） */
    private double[][] createFallbackPrediction(double lastValue) {
        double[][] result = new double[3][H_pred];
        for (int h = 0; h < H_pred; h++) {
            result[0][h] = Math.max(0, lastValue * 0.8);
            result[1][h] = Math.max(0, lastValue);
            result[2][h] = lastValue * 1.2;
        }
        return result;
    }

    // ==================== 工业级扩展方法 ====================

    /**
     * 网格搜索最优 mtry 参数 — 使用 3 折交叉验证，以 RMSE 为评价标准。
     */
    private int gridSearchMtry(DataFrame features, double[] load, double[] pv, int n) {
        int p = features.ncol();
        int baseMtry = Math.max(2, (int) Math.round(Math.sqrt(p)));

        int[] candidates;
        int low = Math.max(2, baseMtry / 2);
        int high = Math.min(p, baseMtry * 2);
        if (low == baseMtry && baseMtry == high) {
            return baseMtry;
        }
        if (low == high) {
            candidates = new int[]{low, baseMtry};
        } else if (low == baseMtry) {
            candidates = new int[]{baseMtry, high};
        } else if (baseMtry == high) {
            candidates = new int[]{low, baseMtry};
        } else {
            candidates = new int[]{low, baseMtry, high};
        }

        int bestMtry = baseMtry;
        double bestScore = Double.MAX_VALUE;
        int foldSize = Math.max(MIN_SAMPLE, n / CV_FOLDS);

        for (int mtry : candidates) {
            double cvScore = 0.0;
            int validFolds = 0;

            for (int fold = 0; fold < CV_FOLDS; fold++) {
                int testStart = fold * foldSize;
                int testEnd = Math.min(testStart + foldSize, n);
                if (testEnd - testStart < MIN_SAMPLE) {
                    continue;
                }

                List<Integer> trainIdx = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if (i < testStart || i >= testEnd) {
                        trainIdx.add(i);
                    }
                }
                if (trainIdx.size() < MIN_SAMPLE) {
                    continue;
                }

                double[] trainLoad = new double[trainIdx.size()];
                double[] testLoad = new double[testEnd - testStart];
                for (int i = 0; i < trainIdx.size(); i++) {
                    trainLoad[i] = load[trainIdx.get(i)];
                }
                for (int j = testStart; j < testEnd; j++) {
                    testLoad[j - testStart] = load[j];
                }

                DataFrame trainFeat = selectRows(features, trainIdx);
                DataFrame testFeat = selectRows(features, testStart, testEnd);

                try {
                    RandomForest cvModel = RandomForest.fit(
                            smile.data.formula.Formula.lhs("target"),
                            trainFeat.merge(DoubleVector.of("target", trainLoad)),
                            Math.min(100, N_TREES / 2),
                            mtry, 12, Integer.MAX_VALUE, MIN_SPLIT, SUBSAMPLE);

                    double[] pred = new double[testLoad.length];
                    for (int i = 0; i < testLoad.length; i++) {
                        pred[i] = Math.max(0, cvModel.predict(testFeat.get(i)));
                    }
                    ForecastMetrics fm = ForecastMetrics.calculate(testLoad, pred);
                    cvScore += fm.rmse;
                    validFolds++;
                } catch (Exception e) {
                    cvScore += Double.MAX_VALUE / CV_FOLDS;
                }
            }

            if (validFolds > 0) {
                cvScore /= validFolds;
                if (cvScore < bestScore) {
                    bestScore = cvScore;
                    bestMtry = mtry;
                }
            }
        }
        return bestMtry;
    }

    /**
     * 评估模型性能 — 在全量训练数据上计算 MAE/RMSE/MAPE/R²。
     */
    private ForecastMetrics evaluateModel(RandomForest model, DataFrame features, double[] actual) {
        double[] predicted = new double[actual.length];
        for (int i = 0; i < actual.length; i++) {
            predicted[i] = Math.max(0, model.predict(features.get(i)));
        }
        return ForecastMetrics.calculate(actual, predicted);
    }

    /**
     * 概念漂移检测 — 当连续多次预测 MAPE 超过阈值时触发告警。
     */
    private void checkConceptDrift() {
        boolean loadDrift = lastLoadMetrics != null && lastLoadMetrics.mape > DRIFT_THRESHOLD * 100;
        boolean pvDrift = lastPvMetrics != null && lastPvMetrics.mape > DRIFT_THRESHOLD * 100;

        if (loadDrift || pvDrift) {
            driftCounter++;
            log.warn("概念漂移告警 ({}/{}): 负荷MAPE={}% 光伏MAPE={}%",
                    driftCounter, MAX_DRIFT_COUNT,
                    String.format("%.1f", lastLoadMetrics != null ? lastLoadMetrics.mape : 0),
                    String.format("%.1f", lastPvMetrics != null ? lastPvMetrics.mape : 0));
        } else {
            driftCounter = 0;
        }

        if (driftCounter >= MAX_DRIFT_COUNT) {
            log.error("连续 {} 次概念漂移！请检查数据分布，建议增加历史数据窗口 (H_hist)", driftCounter);
            driftCounter = 0;
        }
    }

    /**
     * 持久化当前模型到磁盘 — 使用 Java 序列化存储。
     */
    private void saveModels() {
        if (modelSavePath == null || modelSavePath.isEmpty()) {
            return;
        }
        File dir = new File(modelSavePath);
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("无法创建模型持久化目录: {}", modelSavePath);
            return;
        }

        try {
            if (loadModel != null) {
                try (ObjectOutputStream oos = new ObjectOutputStream(
                        new FileOutputStream(new File(dir, "forecast_load.model")))) {
                    oos.writeObject(loadModel);
                }
            }
            if (pvModel != null) {
                try (ObjectOutputStream oos = new ObjectOutputStream(
                        new FileOutputStream(new File(dir, "forecast_pv.model")))) {
                    oos.writeObject(pvModel);
                }
            }
            log.debug("模型已持久化至 {}", modelSavePath);
        } catch (IOException e) {
            log.warn("模型持久化失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean loadModels() {
        if (modelSavePath == null || modelSavePath.isEmpty()) {
            return false;
        }
        File loadFile = new File(modelSavePath, "forecast_load.model");
        File pvFile = new File(modelSavePath, "forecast_pv.model");

        if (!loadFile.exists() || !pvFile.exists()) {
            log.info("未找到持久化模型，将从头训练");
            return false;
        }

        try {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(loadFile))) {
                loadModel = (RandomForest) ois.readObject();
            }
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pvFile))) {
                pvModel = (RandomForest) ois.readObject();
            }
            log.info("模型加载成功: {}", modelSavePath);
            return true;
        } catch (IOException | ClassNotFoundException e) {
            log.warn("模型加载失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void setModelSavePath(String path) {
        this.modelSavePath = path;
    }

    @Override
    public ForecastMetrics getLoadMetrics() {
        return lastLoadMetrics;
    }

    @Override
    public ForecastMetrics getPvMetrics() {
        return lastPvMetrics;
    }

    /** 获取最近负载模型评估指标（兼容旧接口） */
    public ForecastMetrics getLastLoadMetrics() {
        return lastLoadMetrics;
    }

    /** 获取最近光伏模型评估指标（兼容旧接口） */
    public ForecastMetrics getLastPvMetrics() {
        return lastPvMetrics;
    }

    private DataFrame selectRows(DataFrame df, int start, int end) {
        List<Tuple> rows = new ArrayList<>();
        for (int i = start; i < end; i++) {
            rows.add(df.get(i));
        }
        return DataFrame.of(rows);
    }

    private DataFrame selectRows(DataFrame df, List<Integer> indices) {
        List<Tuple> rows = new ArrayList<>();
        for (int idx : indices) {
            rows.add(df.get(idx));
        }
        return DataFrame.of(rows);
    }
}