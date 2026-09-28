import { Accordion, Field, Heading, Input } from "@chakra-ui/react"
import { LuSettings } from "react-icons/lu"
import { useSimulationStore } from "../../../../infrastructure/stores/simulationStore"
import { useSimulationSettingsStore } from "../../../../infrastructure/stores/simulationSettingsStore"

export function SimulationSettings() {
    const {
        tickIntervalMilliseconds,
        setTickIntervalMilliseconds,
        externalSourceCost,
        setExternalSourceCost,
        externalSourceCap,
        setExternalSourceCap,
    } = useSimulationSettingsStore()
    const { isRunning, isPaused } = useSimulationStore()

    return (
        <Accordion.Item value="simulation-parameters">
            <Accordion.ItemTrigger>
                <LuSettings />
                <Heading size="md">仿真参数</Heading>
                <Accordion.ItemIndicator />
            </Accordion.ItemTrigger>
            <Accordion.ItemContent>
                <Accordion.ItemBody display="flex" flexDirection="column" gap="4">
                    <Field.Root>
                        <Field.Label>仿真步长 (毫秒)</Field.Label>
                        <Input
                            disabled={isRunning || isPaused}
                            type="number"
                            value={tickIntervalMilliseconds}
                            onChange={e => setTickIntervalMilliseconds(Number(e.target.value))}
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>外部购电成本</Field.Label>
                        <Input
                            disabled={isRunning || isPaused}
                            type="number"
                            value={externalSourceCost}
                            onChange={e => setExternalSourceCost(Number(e.target.value))}
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>外部电网容量上限</Field.Label>
                        <Input
                            disabled={isRunning || isPaused}
                            type="number"
                            value={externalSourceCap}
                            onChange={e => setExternalSourceCap(Number(e.target.value))}
                        />
                    </Field.Root>
                </Accordion.ItemBody>
            </Accordion.ItemContent>
        </Accordion.Item>
    )
}