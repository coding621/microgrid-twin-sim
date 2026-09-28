import {
    Accordion,
    Button,
    Dialog,
    DialogBackdrop,
    DialogPositioner,
    Field,
    Flex,
    Heading,
    Input,
    Portal,
    Text,
} from "@chakra-ui/react"
import { LuCloud, LuExternalLink } from "react-icons/lu"
import { useUpdateWeather } from "../../../../infrastructure/fetching"
import { useSimulationStore } from "../../../../infrastructure/stores/simulationStore"
import { useWeatherStore } from "../../../../infrastructure/stores/weatherStore"

export function WeatherSettings() {
    const { weather } = useWeatherStore()
    const { isRunning, isPaused } = useSimulationStore()
    const { mutate: updateWeather } = useUpdateWeather()

    return (
        <Accordion.Item value="weather-settings">
            <Accordion.ItemTrigger>
                <LuCloud />
                <Heading size="md">天气参数</Heading>
                <Accordion.ItemIndicator />
            </Accordion.ItemTrigger>
            <Accordion.ItemContent>
                <Accordion.ItemBody display="flex" flexDirection="column" gap="4">
                    {isRunning || isPaused ? (
                        <Flex alignItems="center" gap="5">
                            <Text>仿真运行中仍可实时注入天气数据</Text>
                            <Dialog.Root placement="center">
                                <Dialog.Trigger asChild>
                                    <Button size="xs" variant="outline">
                                        <LuExternalLink />
                                    </Button>
                                </Dialog.Trigger>
                                <Portal>
                                    <DialogBackdrop />
                                    <DialogPositioner>
                                        <Dialog.Content>
                                            <Dialog.Header>
                                                <Dialog.Title>注入天气</Dialog.Title>
                                            </Dialog.Header>
                                            <Dialog.Body>
                                                <WeatherForm />
                                            </Dialog.Body>
                                            <Dialog.Footer>
                                                <Dialog.ActionTrigger asChild>
                                                    <Button variant="outline">关闭</Button>
                                                </Dialog.ActionTrigger>
                                                <Dialog.ActionTrigger asChild>
                                                    <Button onClick={() => updateWeather({ body: weather } as any)}>
                                                        注入
                                                        <LuCloud />
                                                    </Button>
                                                </Dialog.ActionTrigger>
                                            </Dialog.Footer>
                                        </Dialog.Content>
                                    </DialogPositioner>
                                </Portal>
                            </Dialog.Root>
                        </Flex>
                    ) : (
                        <WeatherForm />
                    )}
                </Accordion.ItemBody>
            </Accordion.ItemContent>
        </Accordion.Item>
    )
}

function WeatherForm() {
    const { weather, setWeather } = useWeatherStore()

    return (
        <>
            <Field.Root>
                <Field.Label>日间平均温度 (°C)</Field.Label>
                <Input
                    max={100}
                    min={-100}
                    type="number"
                    value={weather.tempMeanDay}
                    onChange={e => setWeather({ ...weather, tempMeanDay: Number(e.target.value) })}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>夜间平均温度 (°C)</Field.Label>
                <Input
                    max={100}
                    min={-100}
                    type="number"
                    value={weather.tempMeanNight}
                    onChange={e => setWeather({ ...weather, tempMeanNight: Number(e.target.value) })}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>日出时刻 (tick)</Field.Label>
                <Input
                    min={1}
                    type="number"
                    value={weather.sunriseTick}
                    onChange={e => setWeather({ ...weather, sunriseTick: Number(e.target.value) })}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>日落时刻 (tick)</Field.Label>
                <Input
                    min={1}
                    type="number"
                    value={weather.sunsetTick}
                    onChange={e => setWeather({ ...weather, sunsetTick: Number(e.target.value) })}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>日照峰值时刻 (tick)</Field.Label>
                <Input
                    min={1}
                    type="number"
                    value={weather.sunPeakTick}
                    onChange={e => setWeather({ ...weather, sunPeakTick: Number(e.target.value) })}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>峰值辐照度 (W/m²)</Field.Label>
                <Input
                    min={0.01}
                    type="number"
                    value={weather.gPeak}
                    onChange={e => setWeather({ ...weather, gPeak: Number(e.target.value) })}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>最低温度时刻 (tick)</Field.Label>
                <Input
                    min={1}
                    type="number"
                    value={weather.tempMinTick}
                    onChange={e => setWeather({ ...weather, tempMinTick: Number(e.target.value) })}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>温度噪声标准差</Field.Label>
                <Input
                    min={0.01}
                    type="number"
                    value={weather.sigmaT}
                    onChange={e => setWeather({ ...weather, sigmaT: Number(e.target.value) })}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>辐照度噪声标准差</Field.Label>
                <Input
                    min={0.01}
                    type="number"
                    value={weather.sigmaG}
                    onChange={e => setWeather({ ...weather, sigmaG: Number(e.target.value) })}
                />
            </Field.Root>
        </>
    )
}