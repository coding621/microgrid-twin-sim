# ⚡ Microgrid Twin Sim — 微电网数字孪生仿真系统

基于 **Spring Boot 3.3** + **React 19** 的全栈微电网数字孪生仿真平台。系统以离散时间步（tick）推进，在每个步长内模拟光伏发电、电池储能、建筑负载的物理行为，并通过智能调度策略实现微电网内部的能量自平衡。

---

## ✨ 核心特性

| 模块 | 说明 |
|------|------|
| 🔬 **物理建模** | 光伏发电模型、锂电池充放电模型、建筑电力负荷模型、外部电网交互模型 |
| 🧠 **智能预测** | 4 种可切换预测策略：随机森林（SMILE RF）、统计时序、相似日匹配（k-NN）、加权集成预测 |
| 📐 **优化调度** | 基于 Google OR-Tools 线性规划（LP）的确定性调度器，在预测视野内求解最优充放电策略 |
| 🤝 **实时协商** | CNP（合同网协议）实时协商机制，处理短期功率缺额与盈余 |
| 🎯 **场景分析** | 分位数树（3 场景）与蒙特卡洛（50 场景）两种场景生成方式，支持鲁棒决策 |
| ⚠️ **事件注入** | 支持组件故障、负荷尖峰、停电等异常事件实时注入，验证系统韧性 |
| 📡 **实时推送** | WebSocket（STOMP 协议）实时推送每个 tick 的仿真状态与聚合指标 |
| 🗺️ **可视化大屏** | 基于 MapLibre GL 的地图可视化，支持设备拖拽放置、实时图表监控 |

---

## 🏗️ 系统架构

```
┌──────────────────────────────────────────────────┐
│                   Frontend (React 19)             │
│  Chakra UI · MapLibre GL · Recharts · DnD Kit   │
│         HTTP REST + WebSocket STOMP              │
└──────────────────────┬───────────────────────────┘
                       │
┌──────────────────────┴───────────────────────────┐
│                Backend (Spring Boot 3.3)          │
│  ┌─────────────┐  ┌──────────────────────────┐   │
│  │  REST API   │  │   WebSocket (STOMP)       │   │
│  │  启动/停止   │  │   /topic/tickData        │   │
│  │  暂停/加速   │  │   /topic/metrics         │   │
│  └──────┬──────┘  └───────────────────────────┘   │
│         │                                         │
│  ┌──────┴──────────────────────────────────────┐  │
│  │          SimulationEngineService             │  │
│  │          ScheduledExecutor 主循环             │  │
│  │   每 tick: Load → Source → Storage → Agg     │  │
│  └──────┬──────────────────────────────────────┘  │
│         │                                         │
│  ┌──────┴──────────────────────────────────────┐  │
│  │            AggregatorService                 │  │
│  │  CNP协商 · 历史缓冲 · 预测规划 · 动作调度    │  │
│  └──────┬──────────────────────────────────────┘  │
│         │                                         │
│  ┌──────┴──────┐  ┌──────────┐  ┌─────────────┐  │
│  │ Forecaster  │  │ Planner  │  │ Scenario Gen │  │
│  │ 预测引擎    │  │ LP调度器 │  │ 场景生成器   │  │
│  └─────────────┘  └──────────┘  └─────────────┘  │
└──────────────────────────────────────────────────┘
```

### 仿真主循环

每个 tick 的执行流程：

1. **停电检查** — 若处于黑启动状态，跳过当前 tick
2. **实体执行** — Load.onTick() → Source.onTick() → Storage.onTick()
3. **聚合器调度** — CNP 协商 → 历史记录 → 预测与规划 → 动作执行
4. **数据推送** — WebSocket 实时推送 tickData / metrics

---

## 📁 项目结构

```
microgrid-twin-sim/
├── microgrid-twin-sim-backend/    # Spring Boot 后端
│   ├── src/main/java/com/microgrid/sim/
│   │   ├── controller/            # REST API 控制器
│   │   │   ├── SimulationController.java   # 仿真生命周期管理
│   │   │   └── EventController.java        # 事件注入接口
│   │   ├── service/               # 核心服务层
│   │   │   ├── SimulationEngineService.java   # 仿真主引擎
│   │   │   ├── SimulationConfigService.java   # 配置解析
│   │   │   ├── WeatherService.java            # 天气模拟
│   │   │   └── LogAggregatorService.java      # 日志聚合
│   │   ├── simulation/            # 仿真领域模型
│   │   │   ├── base/              # 抽象实体基类
│   │   │   │   ├── AbstractEnergySourceEntity.java
│   │   │   │   ├── AbstractEnergyStorageEntity.java
│   │   │   │   └── AbstractLoadEntity.java
│   │   │   ├── forecast/          # 预测引擎
│   │   │   │   ├── ForecasterRegistry.java
│   │   │   │   ├── RandomForestForecaster.java
│   │   │   │   ├── StatisticalForecaster.java
│   │   │   │   ├── SimilarDayForecaster.java
│   │   │   │   └── EnsembleForecaster.java
│   │   │   ├── planner/           # 优化调度器
│   │   │   │   └── DeterministicPlanner.java
│   │   │   ├── scenario/          # 场景生成器
│   │   │   │   ├── MonteCarloGenerator.java
│   │   │   │   └── QuantileTreeGenerator.java
│   │   │   └── AggregatorService.java   # 聚合调度中心
│   │   ├── ws/                    # WebSocket 层
│   │   │   ├── config/            # STOMP & CORS 配置
│   │   │   ├── dto/               # 消息体定义
│   │   │   └── service/           # 推送服务
│   │   └── registry/              # 实体状态注册中心
│   └── pom.xml                    # Maven 依赖配置
│
├── microgrid-twin-sim-front/      # React 前端
│   └── src/
│       ├── components/
│       │   ├── dashboard/         # 仪表盘图表面板
│       │   ├── map/               # 地图标记与拖拽交互
│       │   ├── simulationRuntime/ # 运行时日志窗口
│       │   ├── simulationSetup/   # 仿真配置与设备卡片
│       │   └── ui/                # 通用 UI 组件
│       ├── infrastructure/
│       │   ├── fetching/          # HTTP API 客户端
│       │   ├── stores/            # Zustand 状态管理
│       │   └── websocket/         # WebSocket STOMP 通信
│       ├── routes/                # TanStack Router 路由
│       └── services/              # 地图 & 仿真配置
│
├── data/models/                   # 预训练预测模型文件
│   ├── forecast_load.model
│   └── forecast_pv.model
└── .gitignore
```

---

## 🚀 快速开始

### 环境要求

| 工具 | 版本要求 |
|------|----------|
| JDK | 21+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| npm | 9+ |

### 1. 克隆项目

```bash
git clone <repo-url>
cd microgrid-twin-sim
```

### 2. 启动后端

```bash
cd microgrid-twin-sim-backend
mvn spring-boot:run
```

后端服务默认运行在 **http://localhost:8081**。

### 3. 启动前端

```bash
cd microgrid-twin-sim-front
npm install
npm run dev
```

前端开发服务器默认运行在 **http://localhost:4200**。

### 4. 访问系统

浏览器打开 **http://localhost:4200**，即可进入微电网数字孪生仿真系统。

---

## ⚙️ 配置说明

### 后端配置

后端支持通过 `application.properties` 和 `simulation-config.json` 进行配置。

#### 服务端口

```properties
# microgrid-twin-sim-backend/src/main/resources/application.properties
server.port=8081
```

#### 仿真参数

```jsonc
// microgrid-twin-sim-backend/src/main/resources/simulation-config.json
{
  "simulation": {
    "tickIntervalMillis": 1000,   // tick 间隔（毫秒）
    "externalSourceCost": 50.0,   // 外部电网购电成本
    "externalSourceCap": 500.0,   // 外部电网容量上限
    "forecast": {
      "H_hist": 24,               // 历史窗口（tick）
      "H_pred": 4,                // 预测窗口（tick）
      "replanEvery": 2,           // 重规划间隔
      "enablePredictive": 1       // 启用预测性调度
    },
    "weather": {
      "sunriseTick": 6,           // 日出时刻
      "sunsetTick": 18,           // 日落时刻
      "gPeak": 1000.0             // 峰值辐照度（W/m²）
    },
    "agents": [/* 设备实体配置 */]
  }
}
```

### 前端配置

```bash
# microgrid-twin-sim-front/.env
VITE_API_BASE_URL=http://localhost:8081
```

---

## 🔌 API 概览

### REST API

| 方法 | 端点 | 说明 |
|------|------|------|
| `POST` | `/api/simulation/start` | 启动仿真 |
| `POST` | `/api/simulation/stop` | 停止仿真 |
| `POST` | `/api/simulation/pause` | 暂停仿真 |
| `POST` | `/api/simulation/resume` | 恢复仿真 |
| `POST` | `/api/simulation/speed` | 设置仿真速度倍率 |
| `POST` | `/api/event/fault` | 注入设备故障事件 |
| `POST` | `/api/event/load-spike` | 注入负荷尖峰事件 |
| `POST` | `/api/event/blackout` | 注入停电事件 |

### WebSocket 端点

| 端点 | 协议 | 说明 |
|------|------|------|
| `ws://localhost:8081/ws/data` | STOMP over WebSocket | 数据流端点 |
| `/topic/tickData` | 订阅 | 每个 tick 的实体状态推送 |
| `/topic/metrics` | 订阅 | 每 N 个 tick 的聚合指标推送 |

---

## 🧪 技术栈

### 后端

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.3.4 |
| 语言 | Java | 21 |
| 构建 | Maven | — |
| 优化求解 | Google OR-Tools | 9.11.4210 |
| 机器学习 | SMILE | 3.1.1 |
| 数学计算 | Apache Commons Math3 | 3.6.1 |
| 随机数 | Apache Commons RNG | 1.5 |
| 消息推送 | WebSocket + STOMP | — |
| API 文档 | SpringDoc OpenAPI | 2.3.0 |

### 前端

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 19 |
| 构建 | Vite | 6 |
| UI 库 | Chakra UI | 3.13 |
| 地图 | react-map-gl + MapLibre GL | 8 / 5 |
| 路由 | TanStack Router | 1.114 |
| 状态管理 | Zustand | 5 |
| 数据请求 | TanStack React Query | 5.73 |
| 图表 | Recharts | 2.15 |
| 拖拽 | @dnd-kit/core | 6 |
| WebSocket | @stomp/rx-stomp | 2 |

---

## 📄 License

---

## 👥 贡献

欢迎提交 Issue 和 Pull Request。

---

## 📮 联系方式

如有问题或建议，请通过 Issue 联系我。