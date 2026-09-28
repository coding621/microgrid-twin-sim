# Microgrid Twin Sim - 微电网数字孪生系统

基于 Spring Boot 的微电网数字孪生仿真平台。系统以离散时间步（tick）推进，在每个步长内模拟光伏发电、电池储能、建筑负载的物理行为，并通过智能调度策略实现微电网内部的能量自平衡。

## 项目概述

本系统构建了一个完整的微电网数字孪生环境，包含以下能力：

- **物理建模**：光伏组件发电模型、锂电池充放电模型、建筑用电负荷模型、外部电网交互模型
- **智能预测**：多算法可切换的概率预测器，支持随机森林、统计时序、相似日、集成预测四种策略
- **优化调度**：基于 OR-Tools 线性规划的确定性调度器，在预测视野内求解最优充放电策略
- **实时协商**：CNP（合同网协议）实时协商机制，处理短期功率缺额和盈余
- **场景分析**：支持分位数树（3 场景）和蒙特卡洛（50 场景）两种场景生成方式
- **事件注入**：支持组件故障、负荷尖峰、停电等异常事件的实时注入
- **数据推送**：通过 WebSocket（STOMP 协议）向客户端实时推送每个 tick 的仿真状态和聚合指标

---

## 系统架构

### 整体层次

```
┌─────────────────────────────────────────────────────┐
│                   REST API 层                        │
│   SimulationController  │  EventController           │
│   启动/停止/暂停/加速      │  故障/尖峰/停电注入        │
└─────────────────────────┬───────────────────────────┘
                          │
┌─────────────────────────┴───────────────────────────┐
│                   仿真引擎层                          │
│   SimulationEngineService（ScheduledExecutor 主循环） │
│   每 tick 按序驱动：Load → Source → Storage → Agg    │
└─────────────────────────┬───────────────────────────┘
                          │
┌─────────────────────────┴───────────────────────────┐
│                   业务逻辑层                          │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ 光伏发电  │  │ 电池储能  │  │ AggregatorService │  │
│  │ Source   │  │ Storage  │  │ CNP协商·预测·规划  │  │
│  └──────────┘  └──────────┘  └───────────────────┘  │
│  ┌──────────┐  ┌──────────┐                         │
│  │ 建筑负荷  │  │ 外部电网  │                         │
│  │ Load     │  │ External │                         │
│  └──────────┘  └──────────┘                         │
└─────────────────────────┬───────────────────────────┘
                          │
┌─────────────────────────┴───────────────────────────┐
│                   数据与推送层                        │
│  AgentStateRegistry  │  WebSocket (STOMP)            │
│  实体状态中心化注册     │  /topic/tickData 实时推送     │
│                       │  /topic/metrics 指标推送      │
└─────────────────────────────────────────────────────┘
```

### 仿真主循环

每个仿真步长（tick）的执行流程如下：

```
tick N 开始
  │
  ├─→ [1] 停电检查：若处于黑启动，跳过本 tick
  │
  ├─→ [2] 实体执行（按固定顺序）：
  │      Load.onTick()       → 计算当前负荷需求
  │      Source.onTick()     → 计算当前光伏出力
  │      Storage.onTick()    → 施加自放电衰减
  │
  ├─→ [3] Aggregator.onTick()：
  │      3a. 收集实时产消数据
  │      3b. CNP 协商（处理短期功率缺额/盈余）
  │      3c. 写入历史环形缓冲区
  │      3d. 到达重规划时刻 → 预测 + 场景生成 + 线性规划
  │      3e. 执行调度动作队列中的下一个动作
  │      3f. 更新状态注册中心
  │
  ├─→ [4] 推送 tickData WebSocket 消息
  │
  ├─→ [5] 每隔 N tick 推送 metrics WebSocket 消息
  │
  └─→ 调度下一个 tick
```

---

## 核心业务逻辑

### 1. 光伏发电模型

光伏组件发电功率基于标准物理模型计算：

```
电池温度：Tcell = Tamb + G × (NOCT - 20) / 800
发电功率：P = N × η × A × G × [1 + β × (Tcell - 25)]
```

| 参数 | 含义 | 典型值 |
|------|------|--------|
| N | 光伏板数量 | 500 |
| η | 组件效率 | 0.20 |
| A | 组件面积 (m²) | 1.6 |
| G | 当前辐照度 (W/m²) | 由天气模型提供 |
| β | 温度系数 (/°C) | -0.0038 |
| NOCT | 额定工作电池温度 (°C) | 45 |

### 2. 电池储能模型

电池的荷电状态（SoC）按以下动态方程演化：

```
soc(k+1) = soc(k) + ηC × chg(k) - dsg(k) / ηD - σ × soc(k)
```

其中：
- **ηC / ηD**：充放电效率（典型值 0.94 / 0.92）
- **C-rate**：限制最大充放电功率 = Crate × 电池容量
- **σ**：自放电率（3.9 × 10⁻⁴ / tick）

电池在 CNP 协商中通过生成充放电提案参与实时调度，并作为确定性规划中的核心决策变量。

### 3. 天气模型

天气模型模拟日照辐照度和环境温度的日变化，为光伏发电模型提供输入：

**辐照度**（日出至日落间正弦曲线，叠加高斯噪声）：

```
G = gPeak × sin(π × (tick - rise) / (set - rise)) + σG × gPeak × N(0,1)
```

**温度**（日周期函数，包含辐照度加热效应）：

```
T = Tnight + (Tday - Tnight) × sin(π × (tick - rise) / (set - rise)) + 0.02 × G + σT × N(0,1)
```

### 4. CNP 合同网协商协议

CNP（Contract Net Protocol）负责处理每个 tick 的实时功率不平衡问题，在长期预测规划之外提供短期快速响应：

**功率短fall处理**（净需求 > 0，即负荷超出光伏出力）：
1. 向所有电池请求放电提案
2. 按放电成本从低到高排序，依次接受
3. 剩余缺口从外部电网购入（高价，鼓励内部自平衡）

**功率盈余处理**（净出力 > 0，即光伏超出负荷需求）：
1. 向所有电池请求充电提案
2. 按充电利润从高到低排序，依次接受
3. 剩余盈余出售给外部电网

### 5. 多算法概率预测模型（核心）

预测模块是整个系统智能调度的大脑。系统采用**策略模式（Strategy Pattern）**构建可扩展的多算法预测架构，通过统一接口 `Forecaster` 和注册表 `ForecasterRegistry` 实现算法的热切换。目前已内置四种预测器，覆盖从零依赖轻量统计到工业级机器学习的完整层次。

#### 5.1 策略模式架构

```
                     ┌──────────────────┐
                     │    Forecaster     │  ← 统一接口
                     │  (interface)      │
                     │                   │
                     │ update()          │
                     │ predictLoad()     │
                     │ predictPv()       │
                     │ getMetrics()      │
                     │ loadModels()      │
                     │ getName()         │
                     └───────┬───────────┘
                             │
          ┌──────────────────┼──────────────────┬──────────────────┐
          │                  │                  │                  │
  ┌───────┴────────┐ ┌──────┴───────┐ ┌───────┴────────┐ ┌───────┴────────┐
  │RandomForest    │ │Statistical   │ │SimilarDay      │ │Ensemble        │
  │Forecaster      │ │Forecaster    │ │Forecaster      │ │Forecaster      │
  ├────────────────┤ ├──────────────┤ ├────────────────┤ ├────────────────┤
  │• SMILE RF      │ │• 日均曲线    │ │• k=5 相似日   │ │• 4 法融合      │
  │• 500 棵树      │ │• 线性趋势    │ │• MSE 距离     │ │• 工作日/周末   │
  │• 19 维特征     │ │• 残差噪声    │ │• 加权次日     │ │ 自适应权重     │
  │• 网格搜索调参  │ │• 零依赖      │ │• 趋势缩放     │ │• 贝叶斯后验   │
  └────────────────┘ └──────────────┘ └────────────────┘ └────────────────┘
          ↑ 默认              ↑ 最轻量          ↑ 历史模式        ↑ 最高精度
```

**动态选择**：在仿真配置的 `forecast` 块中设置 `forecastMethod` 字段即可切换：

```json
{
  "forecast": {
    "forecastMethod": "random_forest",
    "H_hist": 24,
    "H_pred": 4
  }
}
```

可选值：`random_forest` | `statistical` | `similar_day` | `ensemble`

**新增算法**：只需实现 `Forecaster` 接口并在 `ForecasterRegistry` 的静态工厂表中注册一行，`AggregatorService` 无需任何改动。

#### 5.2 RandomForestForecaster — 随机森林概率预测（默认）

基于 **SMILE Random Forest** 实现，是本系统的默认预测器，提供最高精度的工业级概率预测。

##### 5.2.1 模型超参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 树数量 (N_TREES) | 500 | 工业级需要 500+ 以保证预测稳定性 |
| 最大深度 (MAX_DEPTH) | 20 | 允许捕获复杂模式的同时防止过拟合 |
| 叶节点最小样本 (MIN_SAMPLE) | 20 | 增大以提升泛化能力 |
| 子采样比例 (SUBSAMPLE) | 0.7 | 每棵树随机采样 70% 数据 |
| 最小分割样本 (MIN_SPLIT) | 5 | 节点分裂所需最少样本 |

##### 5.2.2 特征工程（19 维）

```
┌──────────────────────────────────────────────────────┐
│ 自回归特征（4 维）                                      │
│  lag1_load, lag2_load  — 负载的一阶、二阶滞后           │
│  lag1_pv,   lag2_pv    — 光伏的一阶、二阶滞后           │
├──────────────────────────────────────────────────────┤
│ 移动平均与趋势（4 维）                                   │
│  load_ma3, pv_ma3      — 3 步移动平均                  │
│  load_trend, pv_trend  — 当前值 - 7 步前值             │
├──────────────────────────────────────────────────────┤
│ 温度衍生特征（4 维）                                     │
│  temp_current           — 当前温度                     │
│  temp_lag1              — 温度一阶滞后                  │
│  temp_deviation         — 温度偏离移动平均的偏差          │
│  temp_ma                — 温度 3 步移动平均             │
├──────────────────────────────────────────────────────┤
│ 时间循环编码（7 维）                                     │
│  hour_sin, hour_cos     — 小时的正弦/余弦编码 (周期 24)  │
│  dow_sin,  dow_cos      — 星期几的正弦/余弦 (周期 7)     │
│  month_sin, month_cos   — 月份的正弦/余弦 (周期 12)     │
│  is_weekend             — 是否周末 (0/1)               │
└──────────────────────────────────────────────────────┘
```

**设计原因**：
- **自回归特征**：捕捉时间序列的短时依赖关系，lag1 反映最近趋势，lag2 捕捉二阶惯性
- **移动平均**：平滑短期波动，提取局部均值水平
- **趋势特征**：检测数据的方向性变化（上升/下降）
- **时间循环编码**：使用 sin/cos 变换将周期性离散变量（小时、星期、月份）连续化，让模型感知"23 点离 0 点很近"、"12 月离 1 月很近"的循环特性
- **温度特征**：温度直接影响光伏效率（温度系数）和空调负荷，是光伏和负载预测的关键交叉变量

##### 5.2.3 网格搜索自动调参

在模型训练前，系统通过 **3 折交叉验证** 自动搜索最优 mtry（每棵树的随机特征子集大小）：

```
搜索范围：从 floor(√19 / 2) ≈ 2 到 ceil(√19 × 2) ≈ 10
评价标准：RMSE（均方根误差）
策略：每折用缩小森林（250 棵树代替 500 棵）快速评估，节省训练时间
```

##### 5.2.4 训练流程

```
原始数据 → 数据清洗（前值填充无效值）
         → 变异系数检查（CV < 5% 发出低方差告警）
         → 19 维特征工程
         → 网格搜索最优 mtry
         → Random Forest 训练（独立训练 loadModel + pvModel）
         → 四维评估（MAE / RMSE / MAPE / R²）
         → 概念漂移检测
         → 模型持久化到 disk
```

##### 5.2.5 递归多步预测

与单步预测不同，本系统采用 **递归（滚动）预测** 策略实现多步外推：

```
for h = 0 to H_pred:
    [1] 用当前演化状态构建 19 维特征向量
    [2] 获取 500 棵树的预测值
    [3] 基于经验分布计算 q05 / q50 / q95 分位数
    [4] 更新演化状态（数组左移 + 填入本步预测值）
        - 负载模型：预测值填入 evolvedLoad，光伏用日照模型估算
        - 光伏模型：预测值填入 evolvedPv，负载用日模式估算
    [5] 温度用持续性预测（含季节性调整）演化
```

##### 5.2.6 评估体系与概念漂移检测

四维评估指标（MAE / RMSE / MAPE / R²）在训练完成后自动计算。系统持续监控预测质量，当**连续 3 次** MAPE 超过 25% 时触发告警，提示数据分布可能已发生显著变化。

#### 5.3 StatisticalForecaster — 统计时序预测

零外部依赖的轻量级预测器，基于经典时间序列分解，适合算力受限或需要快速冷启动的场景。

```
算法：日均曲线 + 线性趋势 + 残差噪声
——————————————————————————————————————
[1] 将历史数据按 24 小时切分为天
[2] 计算日均曲线（24 个时间槽的均值）
[3] 对日总量做线性拟合，外推预测日总量
[4] 预测值 = 日均曲线 × (预测总量 / 曲线总量)
[5] 基于历史残差的方差估计不确定性
[6] 输出 q05/q50/q95 分位数（±1.645σ）
```

**特点**：无需训练、无需模型文件、零冷启动成本、极低 CPU 开销。

#### 5.4 SimilarDayForecaster — 相似日预测

基于 k-最近邻的非参数预测器，利用历史模式匹配进行预测。

```
算法：k=5 相似日加权平均
——————————————————————————————————————
[1] 将历史数据按天切分
[2] 以最近一天为查询基准
[3] 计算历史上每一天与基准的 MSE 距离
[4] 取距离最小的 5 天
[5] 按相似度倒数加权平均这些天的次日曲线
[6] 用最近一天的总量比例做趋势缩放
[7] 基于相似日间的方差估计不确定性
```

**适用场景**：数据具有明显的日周期模式，近期趋势平稳。

#### 5.5 EnsembleForecaster — 多策略集成预测

组合四种子方法并加权融合，自动检测工作日/周末切换权重配置。

```
内置 4 种子方法：
——————————————————————————————————————
[1] 同时刻均值   — 历史上同一小时的平均值
[2] 相似日       — k=3 相似日加权平均
[3] 线性趋势     — 日总量线性外推 + 日均曲线
[4] 贝叶斯后验   — 每个时间槽独立贝叶斯更新
```

**自适应权重**：

| 场景 | 同时刻均值 | 相似日 | 线性趋势 | 贝叶斯 |
|------|:---------:|:------:|:--------:|:------:|
| 工作日 | 0.30 | 0.30 | 0.25 | 0.15 |
| 周末 | 0.40 | 0.35 | — | 0.25 |

**周末检测**：最近一天总量低于 7 天均值 × 0.75 则判定为周末。

**特点**：自动适配工作日/周末用电模式，不确定性基于子方法间的分歧估计。

#### 5.6 概率输出格式

所有预测器统一输出三分位数预测矩阵 `[3][H_pred]`：

| 分位数 | 含义 | 应用场景 |
|--------|------|----------|
| q05 | 悲观预测（下界） | 保守调度，确保最低供应 |
| q50 | 中位预测 | 确定性规划的主输入 |
| q95 | 乐观预测（上界） | 风险评估，识别盈余机会 |

分位数间隔在不同预测器中基于不同的统计量估计：随机森林使用树的经验分布，统计/相似日使用残差标准差 × 1.645（正态 90% 置信区间），集成使用子方法间的分歧。

#### 5.7 模型持久化

`RandomForestForecaster` 训练完成后，负载和光伏模型自动通过 Java 序列化存储到 `data/models/` 目录。系统重启后可通过 `loadModels()` 加载已有模型。其他三种预测器（`statistical` / `similar_day` / `ensemble`）为无状态设计，无需持久化。

### 6. 场景生成

基于预测器输出的分位数，系统可生成多种场景用于规划：

**分位数树生成器（QuantileTreeGenerator）**：
- 生成 3 个场景：悲观（权重 15%）、中位（权重 70%）、乐观（权重 15%）
- 快速高效，适用于实时在线规划

**蒙特卡洛生成器（MonteCarloGenerator）**：
- 生成 50 个场景，使用 Gaussian Copula 耦合光伏与负载的相关性（ρ = 0.6，指数相关）
- 每个场景等概率（1/50），覆盖更全面的不确定性空间
- 适用于离线深度分析和风险评估

### 7. 确定性线性规划

规划器使用 **Google OR-Tools GLOP** 求解器，在中位场景下求解最优调度方案：

**决策变量**（每个电池 × H_pred 步）：
- `chg[k]`：第 k 步的充电功率
- `dsg[k]`：第 k 步的放电功率
- `soc[k]`：第 k 步的荷电状态

**约束条件**：
- **功率平衡**：充电 + 负荷 = 放电 + 光伏 + 外部电网
- **SoC 动态**：soc(k+1) = soc(k) + ηC × chg(k) - dsg(k)/ηD
- **充放电上限**：0 ≤ chg/dsg ≤ Crate × Capacity
- **初始 SoC**：等于各电池当前实际 SoC

**优化目标**：
```
minimize  Σ(外部电网交互 + 0.01 × 充放电损耗)
```

外部电网定价远高于内部调度成本，目标函数天然鼓励微电网内部自平衡。

### 8. 事件系统

支持三种异常事件的实时注入，用于测试系统的鲁棒性和应急响应：

| 事件类型 | 注入方式 | 效果 | 恢复机制 |
|----------|----------|------|----------|
| 组件故障 | POST `/api/events/breakComponent` | 指定组件发电/用电降为 0 | 持续 N tick 后自动恢复 |
| 负荷尖峰 | POST `/api/events/spikeLoad` | 负荷 × 指定倍率 | 持续 N tick 后自动恢复 |
| 停电 | POST `/api/events/blackout` | 所有实体跳过执行 | 手动恢复 |

---

## WebSocket 实时数据推送

系统通过 STOMP over WebSocket 向客户端推送两类实时数据：

**端点**：`/ws/data`（支持 SockJS 回退，允许跨域）

**/topic/tickData**（每个 tick 推送）：
- 所有实体的运行状态（需求功率、发电功率、SoC 百分比、故障状态）
- 当前 tick 的预测负荷 / 预测光伏
- 预测误差（实际值 - 预测值）
- Fan chart 区间（悲观/乐观边界）

**/topic/metrics**（每 N tick 推送）：
- 累计总发电量 / 总耗电量
- CNP 协商累计次数
- 绿色能源占比（光伏 / 总消耗 × 100%）
- 预测 RMSE（负荷 / 光伏）
- 单步预测值

---

## 快速启动

### 环境要求

- JDK 21+
- Maven 3.8+

### 启动步骤

```bash
# 1. 进入项目目录
cd microgrid-twin-sim

# 2. 编译项目
mvn clean compile

# 3. 启动应用（默认端口 8081）
mvn spring-boot:run
```

### REST API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/simulation/loadConfig` | 加载 JSON 仿真配置 |
| POST | `/api/simulation/start` | 启动仿真 |
| POST | `/api/simulation/stop` | 停止仿真 |
| POST | `/api/simulation/pause` | 暂停仿真 |
| POST | `/api/simulation/resume` | 恢复仿真 |
| POST | `/api/simulation/speedUp?factor=2.0` | 设置加速因子 |
| GET | `/api/simulation/status` | 查询运行状态 |
| POST | `/api/events/breakComponent` | 注入组件故障事件 |
| POST | `/api/events/spikeLoad` | 注入负荷尖峰事件 |
| POST | `/api/events/blackout` | 注入停电事件 |
| GET | `/api/events/componentStatus` | 查询所有组件状态 |
| GET | `/api/logs` | 查询所有仿真日志 |
| GET | `/api/logs/{agentName}` | 查询指定智能体日志 |

### 仿真配置示例

```json
{
  "simulation": {
    "tickIntervalMillis": 1000,
    "externalSourceCost": "50.0",
    "externalSourceCap": "500.0",
    "metricsPerNTicks": 2,
    "forecast": {
      "forecastMethod": "random_forest",
      "H_hist": 24,
      "H_pred": 4,
      "replanEvery": 2,
      "epsilonBreak": 20.0,
      "enablePredictive": 1,
      "useMC": 0
    },
    "weather": {
      "sunriseTick": 6,
      "sunsetTick": 18,
      "sunPeakTick": 12,
      "gPeak": 1000.0,
      "tempMeanDay": 25.0,
      "tempMeanNight": 15.0,
      "tempMinTick": 5,
      "sigmaG": 0.15,
      "sigmaT": 0.8
    },
    "agents": [
      { "type": "energySource", "name": "SolarPanel1", "noOfPanels": 500, "efficiency": 0.20, "area": 1.6, "tempCoeff": -0.0038, "noct": 45.0 },
      { "type": "energyStorage", "name": "Battery1", "capacity": 300.0, "etaCharge": 0.94, "etaDischarge": 0.92, "cRate": 0.5, "selfDischarge": 3.9e-4, "initialSoC": 30.0 },
      { "type": "load", "name": "Building1", "nominalLoad": 150.0 },
      { "type": "load", "name": "Building2", "nominalLoad": 100.0 }
    ]
  }
}
```

---

## 技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| 应用框架 | Spring Boot 3.3.4 | DI、MVC、整体架构 |
| 实时通信 | STOMP + SockJS | WebSocket 数据推送 |
| 机器学习 | SMILE + 统计 + 相似日 + 集成 | 多算法可切换概率预测 |
| 线性规划 | Google OR-Tools GLOP | 最优充放电调度 |
| 数值计算 | Apache Commons Math3 | 物理模型计算 |
| 随机数 | Apache Commons RNG | 蒙特卡洛场景生成 |
| API 文档 | SpringDoc OpenAPI 2.3.0 | 自动生成 Swagger 文档 |
| 构建 | Maven | 依赖管理与构建 |
| 运行时 | JDK 21 | Java 运行环境 |

---

## 项目结构

```
microgrid-twin-sim/
├── pom.xml
├── src/main/resources/
│   ├── application.properties                     # 服务器配置
│   └── simulation-config.json                     # 默认仿真配置
└── src/main/java/com/microgrid/sim/
    ├── EnergyTwinBootApplication.java             # 启动类
    ├── controller/
    │   ├── SimulationController.java              # 仿真控制 REST API
    │   └── EventController.java                   # 事件注入 REST API
    ├── service/
    │   ├── SimulationEngineService.java           # 仿真主循环引擎
    │   ├── SimulationConfigService.java           # 配置加载与解析
    │   ├── SimulationControlService.java          # 暂停/加速控制
    │   ├── EventControlService.java               # 事件管理
    │   ├── LogAggregatorService.java              # 日志聚合
    │   └── WeatherService.java                    # 天气模型
    ├── simulation/
    │   ├── AggregatorService.java                 # 调度中枢（CNP+预测+规划）
    │   ├── EnergySourceService.java               # 光伏发电源
    │   ├── EnergyStorageService.java              # 储能电池
    │   ├── LoadService.java                       # 用电负荷
    │   ├── ExternalEnergySourceService.java       # 外部电网
    │   ├── base/                                  # 抽象基类
    │   ├── aggregator/AggregatorMetaStore.java    # 电池元数据
    │   ├── forecast/
    │   │   ├── Forecaster.java                    # 预测器统一接口
    │   │   ├── ForecasterRegistry.java            # 预测器注册表（策略模式）
    │   │   ├── RandomForestForecaster.java        # 随机森林概率预测（默认）
    │   │   ├── StatisticalForecaster.java         # 统计时序预测
    │   │   ├── SimilarDayForecaster.java          # 相似日预测
    │   │   ├── EnsembleForecaster.java            # 多策略集成预测
    │   │   └── ForecastMetrics.java               # 评估指标
    │   ├── history/HistoryBuffer.java             # 环形历史缓冲
    │   ├── models/Proposal.java                   # CNP 提案
    │   ├── planner/DeterministicPlanner.java      # LP 线性规划
    │   └── scenario/                              # 场景生成器
    ├── registry/AgentStateRegistry.java           # 状态注册中心
    └── ws/                                        # WebSocket 实时推送
```