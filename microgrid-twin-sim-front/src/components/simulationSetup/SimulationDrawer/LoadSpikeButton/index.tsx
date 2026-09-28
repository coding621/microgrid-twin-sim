import { Button, createListCollection, Dialog, Field, Flex, Heading, Input, Portal, Select } from "@chakra-ui/react"
import { ChartNoAxesCombined } from "lucide-react"
import { useMemo, useRef, useState } from "react"
import { Controller, SubmitHandler, useForm } from "react-hook-form"
import { LuZap } from "react-icons/lu"
import { useLoadSpike } from "../../../../infrastructure/fetching"
import { useEntitiesStore } from "../../../../infrastructure/stores/entitiesStore"
import { toaster } from "../../../ui/toaster"

type LoadSpikeButtonProps = {
    disabled?: boolean
}

interface FormValues {
    buildingId: string
    ticksDuration: number
    loadSpikeRate: number
}

export function LoadSpikeButton({ disabled }: LoadSpikeButtonProps) {
    const containerRef = useRef<HTMLDivElement>(null)
    const [isOpen, setIsOpen] = useState(false)

    const {
        mapEntities: { buildings },
    } = useEntitiesStore()

    const buildingsCollection = useMemo(
        () =>
            createListCollection({ items: buildings.map(building => ({ label: building.name, value: building.id })) }),
        [buildings],
    )

    const defaultBuildingId = buildings[0]?.id || ""

    const { mutate: simulateLoadSpike, isPending: isLoadSpikePending } = useLoadSpike({
        onSuccess: () => {
            toaster.create({
                title: "负荷尖峰已触发",
                type: "success",
            })
            setIsOpen(false)
        },
        onError: () => {
            toaster.create({
                title: "负荷尖峰触发失败",
                type: "error",
            })
            setIsOpen(false)
        },
    })

    const {
        control,
        handleSubmit,
        register,
        formState: { errors },
    } = useForm<FormValues>({
        defaultValues: {
            buildingId: defaultBuildingId,
            ticksDuration: 5,
            loadSpikeRate: 2,
        },
        mode: "onChange",
    })

    const onSubmit: SubmitHandler<FormValues> = (data: FormValues) => {
        simulateLoadSpike({
            name: data.buildingId,
            rate: data.loadSpikeRate,
            ticks: data.ticksDuration,
        })
    }

    return (
        <Dialog.Root
            motionPreset="slide-in-bottom"
            open={isOpen}
            placement="center"
            onOpenChange={e => setIsOpen(e.open)}>
            <Dialog.Trigger asChild>
                <Button disabled={disabled} variant="surface">
                    模拟负荷尖峰 <ChartNoAxesCombined />
                </Button>
            </Dialog.Trigger>
            <Portal>
                <Dialog.Backdrop />
                <Dialog.Positioner>
                    <Dialog.Content ref={containerRef} as="form" onSubmit={handleSubmit(onSubmit)}>
                        <Dialog.Header>
                            <Dialog.Title>
                                <Heading size="lg">模拟负荷尖峰</Heading>
                            </Dialog.Title>
                        </Dialog.Header>
                        <Dialog.Body>
                            <Flex direction="column" gap="4">
                                <Controller
                                    control={control}
                                    name="buildingId"
                                    render={({ field }) => (
                                        <Field.Root invalid={!!errors.buildingId}>
                                            <Field.Label>选择建筑</Field.Label>
                                            <Select.Root
                                                collection={buildingsCollection}
                                                value={[field.value]}
                                                onValueChange={details => field.onChange(details.value[0])}>
                                                <Select.HiddenSelect />
                                                <Select.Control>
                                                    <Select.Trigger>
                                                        <Select.ValueText placeholder="选择建筑" />
                                                    </Select.Trigger>
                                                    <Select.IndicatorGroup>
                                                        <Select.Indicator />
                                                    </Select.IndicatorGroup>
                                                </Select.Control>
                                                <Portal container={containerRef}>
                                                    <Select.Positioner>
                                                        <Select.Content>
                                                            {buildingsCollection.items.map(building => (
                                                                <Select.Item key={building.value} item={building}>
                                                                    {building.label}
                                                                    <Select.ItemIndicator />
                                                                </Select.Item>
                                                            ))}
                                                        </Select.Content>
                                                    </Select.Positioner>
                                                </Portal>
                                            </Select.Root>
                                            <Field.ErrorText>{errors.buildingId?.message}</Field.ErrorText>
                                        </Field.Root>
                                    )}
                                    rules={{ required: "请选择建筑" }}
                                />
                                <Field.Root invalid={!!errors.ticksDuration}>
                                    <Field.Label>持续时间 (tick)</Field.Label>
                                    <Input
                                        {...register("ticksDuration", {
                                            min: {
                                                message: "持续时间至少为1个tick",
                                                value: 1,
                                            },
                                            required: "请输入持续时间",
                                        })}
                                        min={1}
                                        type="number"
                                    />
                                    <Field.ErrorText>{errors.ticksDuration?.message}</Field.ErrorText>
                                </Field.Root>
                                <Field.Root invalid={!!errors.loadSpikeRate}>
                                    <Field.Label>尖峰倍率</Field.Label>
                                    <Input
                                        {...register("loadSpikeRate", {
                                            min: {
                                                message: "尖峰倍率必须大于0",
                                                value: 0.1,
                                            },
                                            required: "请输入尖峰倍率",
                                        })}
                                        min="0.1"
                                        step="0.1"
                                        type="number"
                                    />
                                    <Field.ErrorText>{errors.loadSpikeRate?.message}</Field.ErrorText>
                                </Field.Root>
                            </Flex>
                        </Dialog.Body>
                        <Dialog.Footer>
                            <Dialog.ActionTrigger asChild>
                                <Button type="button" variant="outline">
                                    关闭
                                </Button>
                            </Dialog.ActionTrigger>
                            <Button loading={isLoadSpikePending} loadingText="正在模拟负荷尖峰..." type="submit">
                                模拟负荷尖峰
                                <LuZap />
                            </Button>
                        </Dialog.Footer>
                    </Dialog.Content>
                </Dialog.Positioner>
            </Portal>
        </Dialog.Root>
    )
}