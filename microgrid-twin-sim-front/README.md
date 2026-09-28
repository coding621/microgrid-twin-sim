# 微电网数字孪生系统 - 前端

基于 React + Vite 的微电网数字孪生仿真系统前端，使用高德地图作为地图底图。

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | React 19 |
| 构建 | Vite 6 |
| UI 组件库 | Chakra UI 3.13 |
| 地图 | react-map-gl 8 + maplibre-gl 5 |
| 地图瓦片 | 高德地图 |
| 路由 | @tanstack/react-router |
| 状态管理 | Zustand 5 |
| 后端通信 | @tanstack/react-query + @stomp/rx-stomp (WebSocket) |
| 图表 | Recharts 2.15 |
| 拖拽 | @dnd-kit/core |

## 快速启动

```bash
npm install
npm run dev
```

浏览器访问 <http://localhost:4200>。

## 地图配置

地图配置在 [src/services/mapConfig.ts](src/services/mapConfig.ts)：

- **默认中心点**：北京天安门 (116.397, 39.909)
- **坐标系**：GCJ-02（高德火星坐标系）
- **瓦片源**：高德矢量瓦片，4 子域负载均衡

## 预测算法

系统支持 4 种预测算法，可通过 UI 的预测参数面板动态切换：

| 算法 | 标识 | 说明 |
|------|------|------|
| Random Forest | `random_forest` | 基于 SMILE RF 的集成学习 |
| Statistical | `statistical` | 基于时序分解的轻量统计 |
| Similar Day | `similar_day` | 基于 k-NN 的相似日匹配 |
| Ensemble | `ensemble` | 多种方法加权融合 |

## 目录结构

```
web-gd/
├── src/
│   ├── components/
│   │   ├── dashboard/          # 仪表盘图表
│   │   ├── map/                # 地图标记与拖拽
│   │   ├── simulationRuntime/  # 运行时日志窗口
│   │   ├── simulationSetup/    # 仿真配置面板
│   │   │   ├── EntityCard/     # 设备卡片 (电池/光伏/建筑)
│   │   │   ├── SimulationDrawer/  # 仿真抽屉
│   │   │   └── Toolkit/        # 拖拽工具栏
│   │   └── ui/                 # 通用 UI 组件
│   ├── infrastructure/
│   │   ├── fetching/           # HTTP API 客户端 (openapi-ts 生成)
│   │   ├── stores/             # Zustand 状态仓库
│   │   └── websocket/          # WebSocket (STOMP) 连接
│   ├── routes/                 # TanStack Router 路由
│   └── services/               # 地图配置等
├── .env                        # 环境变量
├── vite.config.ts
├── tsconfig.json
└── package.json
```