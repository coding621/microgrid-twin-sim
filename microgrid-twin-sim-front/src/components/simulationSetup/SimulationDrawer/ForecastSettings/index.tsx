import { Accordion, Field, Heading, Input } from "@chakra-ui/react"
import { LuTrendingUp } from "react-icons/lu"
import { createListCollection, Select } from "@chakra-ui/react"
import { useForecastStore } from "../../../../infrastructure/stores/forecastStore"
import { useSimulationStore } from "../../../../infrastructure/stores/simulationStore"

const forecastMethods = createListCollection({
    items: [
        { label: "Random Forest (RF)", value: "random_forest" },
        { label: "Statistical (STS)", value: "statistical" },
        { label: "Similar Day (SIM)", value: "similar_day" },
        { label: "Ensemble (ENS)", value: "ensemble" },
    ],
})

export function ForecastSettings() {
    const { forecast, setForecast } = useForecastStore()
    const { isRunning, isPaused } = useSimulationStore()

    return (
        <Accordion.Item value="forecast-parameters">
            <Accordion.ItemTrigger>
                <LuTrendingUp />
                <Heading size="md">预测参数</Heading>
                <Accordion.ItemIndicator />
            </Accordion.ItemTrigger>
            <Accordion.ItemContent>
                <Accordion.ItemBody display="flex" flexDirection="column" gap="4">
                    <Field.Root>
                        <Field.Label>历史数据窗口 (H_hist)</Field.Label>
                        <Input
                            disabled={isRunning || isPaused}
                            type="number"
                            value={forecast.H_hist}
                            onChange={e => setForecast({ ...forecast, H_hist: Number(e.target.value) })}
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>预测超前步数 (H_pred)</Field.Label>
                        <Input
                            disabled={isRunning || isPaused}
                            type="number"
                            value={forecast.H_pred}
                            onChange={e => setForecast({ ...forecast, H_pred: Number(e.target.value) })}
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>重规划间隔 (tick)</Field.Label>
                        <Input
                            disabled={isRunning || isPaused}
                            type="number"
                            value={forecast.replanEvery}
                            onChange={e => setForecast({ ...forecast, replanEvery: Number(e.target.value) })}
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>约束松弛参数 (ε)</Field.Label>
                        <Input
                            disabled={isRunning || isPaused}
                            step="0.1"
                            type="number"
                            value={forecast.epsilonBreak}
                            onChange={e => setForecast({ ...forecast, epsilonBreak: Number(e.target.value) })}
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>蒙特卡洛模式 (0/1)</Field.Label>
                        <Input
                            disabled={isRunning || isPaused}
                            max="1"
                            min="0"
                            type="number"
                            value={forecast.useMC}
                            onChange={e => setForecast({ ...forecast, useMC: Number(e.target.value) })}
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>预测算法</Field.Label>
                        <Select.Root
                            collection={forecastMethods}
                            disabled={isRunning || isPaused}
                            value={[forecast.forecastMethod]}
                            onValueChange={e => setForecast({ ...forecast, forecastMethod: e.value[0] })}>
                            <Select.HiddenSelect />
                            <Select.Control>
                                <Select.Trigger>
                                    <Select.ValueText placeholder="选择预测算法" />
                                </Select.Trigger>
                                <Select.IndicatorGroup>
                                    <Select.Indicator />
                                </Select.IndicatorGroup>
                            </Select.Control>
                            <Select.Positioner>
                                <Select.Content>
                                    {forecastMethods.items.map(method => (
                                        <Select.Item item={method} key={method.value}>
                                            {method.label}
                                            <Select.ItemIndicator />
                                        </Select.Item>
                                    ))}
                                </Select.Content>
                            </Select.Positioner>
                        </Select.Root>
                    </Field.Root>
                </Accordion.ItemBody>
            </Accordion.ItemContent>
        </Accordion.Item>
    )
}