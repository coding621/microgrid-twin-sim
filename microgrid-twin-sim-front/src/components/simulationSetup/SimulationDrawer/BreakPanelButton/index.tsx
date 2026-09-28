import { Button, createListCollection, Dialog, Field, Flex, Heading, Input, Portal, Select } from "@chakra-ui/react"
import { useMemo, useRef, useState } from "react"
import { Controller, SubmitHandler, useForm } from "react-hook-form"
import { LuWrench } from "react-icons/lu"
import { useBreakPanel } from "../../../../infrastructure/fetching"
import { useEntitiesStore } from "../../../../infrastructure/stores/entitiesStore"
import { toaster } from "../../../ui/toaster"

type BreakPanelButtonProps = {
    disabled: boolean
}

interface FormValues {
    solarId: string
    ticksDuration: number
}

export function BreakPanelButton({ disabled }: BreakPanelButtonProps) {
    const containerRef = useRef<HTMLDivElement>(null)
    const [isOpen, setIsOpen] = useState(false)

    const {
        mapEntities: { solar },
    } = useEntitiesStore()

    const solarCollection = useMemo(
        () =>
            createListCollection({
                items: solar.map(solar => ({ label: solar.name, value: solar.id })),
            }),
        [solar],
    )

    const defaultSolarId = solar.at(0)?.id || ""

    const { mutate: breakPanel, isPending: isBreakPanelPending } = useBreakPanel({
        onSuccess: () => {
            toaster.create({
                title: "组件故障已触发",
                type: "success",
            })
            setIsOpen(false)
        },
        onError: () => {
            toaster.create({
                title: "组件故障触发失败",
                type: "error",
            })
            setIsOpen(false)
        },
    })

    const {
        control,
        register,
        handleSubmit,
        formState: { errors },
    } = useForm<FormValues>({
        defaultValues: {
            solarId: defaultSolarId,
            ticksDuration: 5,
        },
        mode: "onChange",
    })

    const onSubmit: SubmitHandler<FormValues> = (data: FormValues) => {
        breakPanel({
            name: data.solarId,
            ticks: data.ticksDuration,
        })
    }

    return (
        <Dialog.Root open={isOpen} onOpenChange={e => setIsOpen(e.open)}>
            <Dialog.Trigger asChild>
                <Button disabled={disabled} variant="surface">
                    组件故障
                    <LuWrench />
                </Button>
            </Dialog.Trigger>
            <Portal>
                <Dialog.Backdrop />
                <Dialog.Positioner>
                    <Dialog.Content ref={containerRef} as="form" onSubmit={handleSubmit(onSubmit)}>
                        <Dialog.Header>
                            <Dialog.Title>
                                <Heading size="lg">组件故障</Heading>
                            </Dialog.Title>
                        </Dialog.Header>
                        <Dialog.Body>
                            <Flex direction="column" gap="4">
                                <Controller
                                    control={control}
                                    name="solarId"
                                    render={({ field }) => (
                                        <Field.Root invalid={!!errors.solarId}>
                                            <Field.Label>选择光伏组件</Field.Label>
                                            <Select.Root
                                                collection={solarCollection}
                                                value={[field.value]}
                                                onValueChange={details => field.onChange(details.value[0])}>
                                                <Select.HiddenSelect />
                                                <Select.Control>
                                                    <Select.Trigger>
                                                        <Select.ValueText placeholder="选择光伏组件" />
                                                    </Select.Trigger>
                                                    <Select.IndicatorGroup>
                                                        <Select.Indicator />
                                                    </Select.IndicatorGroup>
                                                </Select.Control>
                                                <Portal container={containerRef}>
                                                    <Select.Positioner>
                                                        <Select.Content>
                                                            {solarCollection.items.map(solar => (
                                                                <Select.Item key={solar.value} item={solar}>
                                                                    {solar.label}
                                                                    <Select.ItemIndicator />
                                                                </Select.Item>
                                                            ))}
                                                        </Select.Content>
                                                    </Select.Positioner>
                                                </Portal>
                                            </Select.Root>
                                            <Field.ErrorText>{errors.solarId?.message}</Field.ErrorText>
                                        </Field.Root>
                                    )}
                                    rules={{ required: "请选择光伏组件" }}
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
                            </Flex>
                        </Dialog.Body>
                        <Dialog.Footer>
                            <Dialog.ActionTrigger asChild>
                                <Button type="button" variant="outline">
                                    关闭
                                </Button>
                            </Dialog.ActionTrigger>
                            <Button loading={isBreakPanelPending} loadingText="正在触发故障..." type="submit">
                                触发故障
                                <LuWrench />
                            </Button>
                        </Dialog.Footer>
                    </Dialog.Content>
                </Dialog.Positioner>
            </Portal>
        </Dialog.Root>
    )
}