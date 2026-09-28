import { EmptyState } from "@chakra-ui/react"
import { BarChart, Battery, LineChart as LineChartIcon, PieChart, Zap, ZapOff } from "lucide-react"
import { ReactNode } from "react"
import { Chart, useChart } from "@chakra-ui/charts"
import { CartesianGrid, Legend, Line, LineChart, Tooltip, XAxis, YAxis } from "recharts"
import { useSimulationRuntimeStore } from "../../infrastructure/stores/simulationRuntimeStore"
import { AllBatteriesChart } from "./AllBatteriesChart"
import { AllSolarPanelsChart } from "./AllSolarPanelsChart"
import { ChartCard } from "./ChartCard"
import { PredictionsLoadChart, PredictionsPvChart } from "./PredictionsChart"
import { DashboardContainer } from "./styles"

type TotalProducedChartData = {
    tickNumber: number
    totalProduced: number
    totalConsumed: number
}

type GreenEnergyRatioChartData = {
    tickNumber: number
    greenEnergyRatio: number
}

type EmptyChartProps = {
    message: string
    icon: ReactNode
}

function EmptyChart({ message, icon }: EmptyChartProps) {
    return (
        <EmptyState.Root alignItems="center" display="flex" height="300px" justifyContent="center" width="100%">
            <EmptyState.Content>
                <EmptyState.Indicator>{icon}</EmptyState.Indicator>
                <EmptyState.Description>{message}</EmptyState.Description>
            </EmptyState.Content>
        </EmptyState.Root>
    )
}

export function Dashboard() {
    const {
        greenEnergyRatioChartData,
        totalProducedChartData,
        batteriesChartData,
        solarPanelsChartData,
        predictionData,
    } = useSimulationRuntimeStore()

    const chart = useChart<TotalProducedChartData>({
        data: totalProducedChartData,
        series: [
            { name: "totalProduced", color: "green.500" },
            { name: "totalConsumed", color: "red.500" },
        ],
    })

    const greenEnergyChart = useChart<GreenEnergyRatioChartData>({
        data: greenEnergyRatioChartData,
        series: [{ name: "greenEnergyRatio", color: "green.500" }],
    })

    return (
        <DashboardContainer>
            <ChartCard title="总能量" tooltip="累积发电量与累积用电量对比">
                {!totalProducedChartData || totalProducedChartData.length === 0 ? (
                    <EmptyChart icon={<Zap size={24} />} message="暂无发电数据" />
                ) : (
                    <Chart.Root chart={chart} height="100%" overflow="hidden" width="100%">
                        <LineChart data={chart.data}>
                            <CartesianGrid stroke={chart.color("border")} vertical={false} />
                            <XAxis
                                axisLine={false}
                                dataKey={chart.key("tickNumber")}
                                label={{
                                    value: "仿真步数",
                                    position: "bottom",
                                }}
                                stroke={chart.color("border")}
                            />
                            <YAxis
                                axisLine={false}
                                dataKey={chart.key("totalProduced")}
                                label={{
                                    value: "累积能量",
                                    position: "left",
                                    angle: -90,
                                }}
                                stroke={chart.color("border")}
                                tickLine={false}
                                tickMargin={10}
                            />
                            <Tooltip animationDuration={100} content={<Chart.Tooltip />} cursor={false} />
                            <Legend content={<Chart.Legend interaction="hover" />} verticalAlign="top" />
                            {chart.series.map(item => (
                                <Line
                                    key={item.name}
                                    dataKey={chart.key(item.name)}
                                    dot={false}
                                    isAnimationActive={false}
                                    opacity={chart.getSeriesOpacity(item.name)}
                                    stroke={chart.color(item.color)}
                                    strokeWidth={2}
                                />
                            ))}
                        </LineChart>
                    </Chart.Root>
                )}
            </ChartCard>
            <ChartCard
                title="绿能占比"
                tooltip="可再生能源占总能源消耗的比例（绿色能源 / 总能源）">
                {!greenEnergyRatioChartData || greenEnergyRatioChartData.length === 0 ? (
                    <EmptyChart icon={<PieChart size={24} />} message="暂无绿能占比数据" />
                ) : (
                    <Chart.Root chart={greenEnergyChart} height="100%" width="100%">
                        <LineChart data={greenEnergyChart.data}>
                            <CartesianGrid stroke={greenEnergyChart.color("border")} vertical={false} />
                            <XAxis
                                axisLine={false}
                                dataKey={greenEnergyChart.key("tickNumber")}
                                label={{
                                    value: "仿真步数",
                                    position: "bottom",
                                }}
                                stroke={greenEnergyChart.color("border")}
                            />
                            <YAxis
                                axisLine={false}
                                dataKey={greenEnergyChart.key("greenEnergyRatio")}
                                label={{
                                    value: "绿能占比",
                                    position: "left",
                                    angle: -90,
                                }}
                                stroke={greenEnergyChart.color("border")}
                                tickLine={false}
                                tickMargin={10}
                            />
                            <Tooltip animationDuration={100} content={<Chart.Tooltip />} cursor={false} />
                            <Legend content={<Chart.Legend interaction="hover" />} verticalAlign="top" />
                            {greenEnergyChart.series.map(item => (
                                <Line
                                    key={item.name}
                                    dataKey={greenEnergyChart.key(item.name)}
                                    dot={false}
                                    isAnimationActive={false}
                                    opacity={greenEnergyChart.getSeriesOpacity(item.name)}
                                    stroke={greenEnergyChart.color(item.color)}
                                    strokeWidth={2}
                                />
                            ))}
                        </LineChart>
                    </Chart.Root>
                )}
            </ChartCard>
            <ChartCard
                title="储能电池状态"
                tooltip="所有储能电池的荷电状态变化曲线">
                {!batteriesChartData || batteriesChartData.length === 0 ? (
                    <EmptyChart icon={<Battery size={24} />} message="暂无电池数据" />
                ) : (
                    <AllBatteriesChart />
                )}
            </ChartCard>
            <ChartCard
                title="光伏发电功率"
                tooltip="所有光伏组件的实时发电功率曲线">
                {!solarPanelsChartData || solarPanelsChartData.length === 0 ? (
                    <EmptyChart icon={<ZapOff size={24} />} message="暂无光伏发电数据" />
                ) : (
                    <AllSolarPanelsChart />
                )}
            </ChartCard>
            <ChartCard
                title="负荷预测"
                tooltip="各建筑负荷的预测值与实际值对比">
                {!predictionData || predictionData.length === 0 ? (
                    <EmptyChart icon={<BarChart size={24} />} message="暂无负荷预测数据" />
                ) : (
                    <PredictionsLoadChart />
                )}
            </ChartCard>
            <ChartCard title="光伏预测" tooltip="光伏发电的预测值与实际值对比">
                {!predictionData || predictionData.length === 0 ? (
                    <EmptyChart icon={<LineChartIcon size={24} />} message="暂无光伏预测数据" />
                ) : (
                    <PredictionsPvChart />
                )}
            </ChartCard>
        </DashboardContainer>
    )
}