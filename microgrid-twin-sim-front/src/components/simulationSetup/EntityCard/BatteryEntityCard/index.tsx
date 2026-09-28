import { Accordion, Heading, HStack, Progress } from "@chakra-ui/react"
import { interpolate } from "motion/react"
import { LuDatabaseZap } from "react-icons/lu"
import { BaseEntityCard, BaseEntityCardProps } from ".."
import { useSimulationStore } from "../../../../infrastructure/stores/simulationStore"
import { useEntitiesStore } from "../../../../infrastructure/stores/entitiesStore"
import { EditableField } from "../../EditableField"

type BatteryEntityCardProps = Omit<BaseEntityCardProps, "type"> & {
    capacity: number
    chargeLevel?: number
    etaCharge?: number
    etaDischarge?: number
    cRate?: number
    selfDischarge?: number
    initialSoC?: number
}

export function BatteryEntityCard({
    id,
    name,
    capacity,
    chargeLevel,
    etaCharge = 0.95,
    etaDischarge = 0.95,
    cRate = 0.5,
    selfDischarge = 0.01,
    initialSoC = 0.5,
}: BatteryEntityCardProps) {
    const { updateBattery } = useEntitiesStore()
    const { isRunning } = useSimulationStore()

    const handleCapacityChange = (value: string) => {
        if (isNaN(Number(value))) return
        updateBattery(id, { capacity: Number(value) })
    }

    const handleEtaChargeChange = (value: string) => {
        if (isNaN(Number(value))) return
        updateBattery(id, { etaCharge: Number(value) })
    }

    const handleEtaDischargeChange = (value: string) => {
        if (isNaN(Number(value))) return
        updateBattery(id, { etaDischarge: Number(value) })
    }

    const handleCRateChange = (value: string) => {
        if (isNaN(Number(value))) return
        updateBattery(id, { cRate: Number(value) })
    }

    const handleSelfDischargeChange = (value: string) => {
        if (isNaN(Number(value))) return
        updateBattery(id, { selfDischarge: Number(value) })
    }

    const handleInitialSoCChange = (value: string) => {
        if (isNaN(Number(value))) return
        updateBattery(id, { initialSoC: Number(value) })
    }

    return (
        <BaseEntityCard id={id} name={name} type="battery">
            <Accordion.Root collapsible variant="enclosed">
                <Accordion.Item value="configuration">
                    <Accordion.ItemTrigger>
                        <LuDatabaseZap />
                        <Heading size="md">电池参数配置</Heading>
                        <Accordion.ItemIndicator />
                    </Accordion.ItemTrigger>
                    <Accordion.ItemContent display="flex" flexDirection="column" gap="4">
                        <EditableField
                            disabled={isRunning}
                            label="容量 (Wh)"
                            value={capacity.toString()}
                            onChange={handleCapacityChange}
                        />
                        <EditableField
                            disabled={isRunning}
                            label="充电效率 (ηC)"
                            value={etaCharge.toString()}
                            onChange={handleEtaChargeChange}
                        />
                        <EditableField
                            disabled={isRunning}
                            label="放电效率 (ηD)"
                            value={etaDischarge.toString()}
                            onChange={handleEtaDischargeChange}
                        />
                        <EditableField
                            disabled={isRunning}
                            label="充放电倍率 (C-Rate)"
                            value={cRate.toString()}
                            onChange={handleCRateChange}
                        />
                        <EditableField
                            disabled={isRunning}
                            label="自放电率"
                            value={selfDischarge.toString()}
                            onChange={handleSelfDischargeChange}
                        />
                        <EditableField
                            disabled={isRunning}
                            label="初始荷电状态 (SoC%)"
                            value={initialSoC.toString()}
                            onChange={handleInitialSoCChange}
                        />
                    </Accordion.ItemContent>
                </Accordion.Item>
            </Accordion.Root>
            {chargeLevel !== undefined && (
                <>
                    <Progress.Root max={capacity} min={0} value={Math.max(0, chargeLevel)}>
                        <HStack gap="2">
                            <Progress.Label>当前电量</Progress.Label>
                            <Progress.Track flex="1">
                                <Progress.Range background={colorMap(chargeLevel / capacity)} />
                            </Progress.Track>
                            <Progress.ValueText>{chargeLevel.toFixed(2)} kWh</Progress.ValueText>
                        </HStack>
                    </Progress.Root>
                </>
            )}
        </BaseEntityCard>
    )
}

export const colorMap = interpolate([0, 1], ["#dc2626", "#22c55e"])