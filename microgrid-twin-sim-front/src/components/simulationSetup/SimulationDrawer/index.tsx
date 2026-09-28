import { Accordion, Button, EmptyState, Flex, Heading, IconButton, Separator } from "@chakra-ui/react"
import { AnimatePresence, motion } from "motion/react"
import { memo, ReactNode, useMemo } from "react"
import {
    LuBuilding,
    LuCirclePause,
    LuCirclePlay,
    LuCircleStop,
    LuDatabaseZap,
    LuSun,
    LuX,
    LuZapOff,
} from "react-icons/lu"
import {
    useBlackout,
    usePauseSimulation,
    useResumeSimulation,
    useStartSimulation,
    useStopSimulation,
} from "../../../infrastructure/fetching"
import { useDrawerStore } from "../../../infrastructure/stores/drawerStore"
import { useEntitiesStore } from "../../../infrastructure/stores/entitiesStore"
import { useForecastStore } from "../../../infrastructure/stores/forecastStore"
import { useSimulationRuntimeStore } from "../../../infrastructure/stores/simulationRuntimeStore"
import { useSimulationSettingsStore } from "../../../infrastructure/stores/simulationSettingsStore"
import { useSimulationStore } from "../../../infrastructure/stores/simulationStore"
import { useWeatherStore } from "../../../infrastructure/stores/weatherStore"
import { BatteryEntityCard } from "../EntityCard/BatteryEntityCard"
import { BuildingEntityCard } from "../EntityCard/BuildingEntityCard"
import { SolarEntityCard } from "../EntityCard/SolarEntityCard"
import { BreakPanelButton } from "./BreakPanelButton"
import { ForecastSettings } from "./ForecastSettings"
import { LoadSpikeButton } from "./LoadSpikeButton"
import { SimulationSettings } from "./SimulationSettings"
import { DrawerRoot } from "./styles"
import { WeatherSettings } from "./WeatherSettings"

const MemoizedBatteryEntityCard = memo(BatteryEntityCard)
const MemoizedBuildingEntityCard = memo(BuildingEntityCard)
const MemoizedSolarEntityCard = memo(SolarEntityCard)
const MemoizedSimulationSettings = memo(SimulationSettings)
const MemoizedWeatherSettings = memo(WeatherSettings)
const MemoizedForecastSettings = memo(ForecastSettings)

export function SimulationDrawer() {
    const { mapEntities } = useEntitiesStore()
    const { isRunning, isPaused } = useSimulationStore()
    const { tickIntervalMilliseconds, externalSourceCost, externalSourceCap } = useSimulationSettingsStore()
    const { weather } = useWeatherStore()
    const { forecast } = useForecastStore()
    const { isOpen, setIsOpen, drawerWidth } = useDrawerStore()
    const { mutate: startSimulation, isPending: isStartPending } = useStartSimulation()
    const { mutate: pauseSimulation, isPending: isPausePending } = usePauseSimulation()
    const { mutate: stopSimulation, isPending: isStopPending } = useStopSimulation()
    const { mutate: simulateBlackout, isPending: isBlackoutPending } = useBlackout()
    const { mutate: resumeSimulation, isPending: isResumePending } = useResumeSimulation()

    const { agentStates } = useSimulationRuntimeStore()

    const jsonStringConfig = useMemo(
        () => ({
            simulation: {
                tickIntervalMillis: tickIntervalMilliseconds,
                externalSourceCost: externalSourceCost.toFixed(1),
                externalSourceCap: externalSourceCap.toFixed(1),
                metricsPerNTicks: 2,
                weather: {
                    sunriseTick: weather.sunriseTick,
                    sunsetTick: weather.sunsetTick,
                    sunPeakTick: weather.sunPeakTick,
                    gPeak: weather.gPeak,
                    tempMeanDay: weather.tempMeanDay,
                    tempMeanNight: weather.tempMeanNight,
                    tempMinTick: weather.tempMinTick,
                    sigmaG: weather.sigmaG,
                    sigmaT: weather.sigmaT,
                },
                forecast: {
                    H_hist: forecast.H_hist,
                    H_pred: forecast.H_pred,
                    replanEvery: forecast.replanEvery,
                    epsilonBreak: forecast.epsilonBreak,
                    enablePredictive: forecast.enablePredictive,
                    useMC: forecast.useMC,
                    forecastMethod: forecast.forecastMethod,
                },
                agents: [
                    ...mapEntities.batteries.map(battery => ({
                        type: "energyStorage",
                        name: battery.id,
                        initialSoC: battery.capacity * battery.initialSoC / 100,
                        capacity: battery.capacity,
                        etaCharge: battery.etaCharge,
                        etaDischarge: battery.etaDischarge,
                        cRate: battery.cRate,
                        selfDischarge: battery.selfDischarge,
                    })),
                    ...mapEntities.solar.map(solar => ({
                        type: "energySource",
                        name: solar.id,
                        noOfPanels: solar.noOfPanels,
                        area: solar.area,
                        efficiency: solar.efficiency,
                        tempCoeff: solar.tempCoeff,
                        noct: solar.noct,
                    })),
                    ...mapEntities.buildings.map(building => ({
                        type: "load",
                        name: building.id,
                        nominalLoad: building.nominalLoad,
                    })),
                ],
            },
        }),
        [mapEntities, tickIntervalMilliseconds, externalSourceCost, externalSourceCap, weather, forecast],
    )

    return (
        <AnimatePresence>
            {isOpen && (
                <DrawerRoot
                    animate={{ translateX: "0%" }}
                    exit={{ translateX: "100%" }}
                    initial={{ translateX: "100%" }}
                    transition={{ duration: 0.2, ease: "circInOut" }}>
                    <Flex direction="column" gap="4" width={drawerWidth}>
                        <Flex
                            backgroundColor="white"
                            borderBottom="1px solid #e2e8f0"
                            direction="row"
                            justify="space-between"
                            paddingBottom="2"
                            paddingTop="6"
                            position="sticky"
                            top="0"
                            zIndex="999">
                            <Heading size="xl">Simulation Setup</Heading>
                            <IconButton rounded="xl" variant="ghost" onClick={() => setIsOpen(false)}>
                                <LuX />
                            </IconButton>
                        </Flex>
                        <Flex direction="column" gap="4">
                            <Heading size="md">事件操作</Heading>
                            <Flex direction="row" flexWrap="wrap" gap="2">
                                <Button
                                    disabled={!isRunning}
                                    loading={isBlackoutPending}
                                    loadingText="正在模拟停电"
                                    variant="surface"
                                    onClick={() => simulateBlackout()}>
                                    模拟停电 <LuZapOff />
                                </Button>
                                <LoadSpikeButton disabled={!isRunning} />
                                <BreakPanelButton disabled={!isRunning} />
                            </Flex>
                        </Flex>
                        <Separator />
                        <MemoizedSettingsAccordion />
                        <Flex direction="column" gap="4">
                            <Heading size="md">储能电池</Heading>
                            {mapEntities.batteries.length ? (
                                mapEntities.batteries.map(battery => (
                                    <MemoizedBatteryEntityCard
                                        key={battery.id}
                                        capacity={battery.capacity}
                                        chargeLevel={agentStates[battery.id]?.stateOfCharge}
                                        id={battery.id}
                                        name={battery.name}
                                    />
                                ))
                            ) : (
                                <EmptyStateMessage
                                    icon={<LuDatabaseZap />}
                                    message="暂无储能电池，从底部工具栏拖拽添加一台"
                                />
                            )}
                            <Heading size="md">光伏阵列</Heading>
                            {mapEntities.solar.length ? (
                                mapEntities.solar.map(solar => (
                                    <MemoizedSolarEntityCard
                                        key={solar.id}
                                        area={solar.area}
                                        currentProduction={agentStates[solar.id]?.production}
                                        efficiency={solar.efficiency}
                                        id={solar.id}
                                        name={solar.name}
                                        noct={solar.noct}
                                        noOfPanels={solar.noOfPanels}
                                        tempCoeff={solar.tempCoeff}
                                    />
                                ))
                            ) : (
                                <EmptyStateMessage
                                    icon={<LuSun />}
                                    message="暂无光伏阵列，从底部工具栏拖拽添加一组"
                                />
                            )}
                            <Heading size="md">建筑负荷</Heading>
                            {mapEntities.buildings.length ? (
                                mapEntities.buildings.map(building => (
                                    <MemoizedBuildingEntityCard
                                        key={building.id}
                                        currentLoad={agentStates[building.id]?.demand}
                                        id={building.id}
                                        name={building.name}
                                        nominalLoad={building.nominalLoad}
                                    />
                                ))
                            ) : (
                                <EmptyStateMessage
                                    icon={<LuBuilding />}
                                    message="暂无建筑负荷，从底部工具栏拖拽添加一栋"
                                />
                            )}
                        </Flex>
                        <Flex direction="row" gap="2" justifyContent="flex-end">
                            <motion.div>
                                <Button
                                    colorPalette={isRunning ? "red" : "green"}
                                    loading={isStartPending || isResumePending || isStopPending}
                                    loadingText={
                                        isStartPending ? "启动中" : isResumePending ? "恢复中" : "停止中"
                                    }
                                    variant="solid"
                                    onClick={() => {
                                        if (isRunning) {
                                            if (isPaused) {
                                                resumeSimulation()
                                            } else {
                                                stopSimulation()
                                            }
                                        } else {
                                            startSimulation(jsonStringConfig as any)
                                        }
                                    }}>
                                    {isRunning ? (isPaused ? "恢复" : "停止") : "启动"}
                                    {isRunning ? isPaused ? <LuCirclePlay /> : <LuCircleStop /> : <LuCirclePlay />}
                                </Button>
                            </motion.div>
                            <Button
                                disabled={!isRunning || isPaused}
                                loading={isPausePending}
                                variant="outline"
                                onClick={() => pauseSimulation()}>
                                暂停
                                <LuCirclePause />
                            </Button>
                        </Flex>
                    </Flex>
                </DrawerRoot>
            )}
        </AnimatePresence>
    )
}

type EmptyStateProps = {
    message: string
    icon: ReactNode
}

function EmptyStateMessage({ message, icon }: EmptyStateProps) {
    return (
        <EmptyState.Root>
            <EmptyState.Content>
                <EmptyState.Indicator>{icon}</EmptyState.Indicator>
                <EmptyState.Description>{message}</EmptyState.Description>
            </EmptyState.Content>
        </EmptyState.Root>
    )
}

function SettingsAccordion() {
    return (
        <Accordion.Root collapsible multiple defaultValue={["simulation-parameters"]} size="lg" variant="enclosed">
            <MemoizedSimulationSettings />
            <MemoizedWeatherSettings />
            <MemoizedForecastSettings />
        </Accordion.Root>
    )
}

const MemoizedSettingsAccordion = memo(SettingsAccordion)