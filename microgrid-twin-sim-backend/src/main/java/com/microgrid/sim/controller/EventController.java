package com.microgrid.sim.controller;

import com.microgrid.sim.registry.AgentStateRegistry;
import com.microgrid.sim.service.EventControlService;
import com.microgrid.sim.service.SimulationEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 事件控制器 - REST API 入口，管理仿真中的随机事件注入。
 *
 * <p>提供以下端点：
 * <ul>
 *   <li>POST /api/events/breakComponent       - 故障组件</li>
 *   <li>POST /api/events/spikeLoad            - 负荷尖峰</li>
 *   <li>POST /api/events/blackout             - 停电事件</li>
 *   <li>GET  /api/events/componentStatus      - 组件状态查询</li>
 *   <li>PUT  /api/configuration/weatherParams - 运行时更新天气参数</li>
 * </ul>
 *
 *
 * @author Coding
 */
@RestController
@RequestMapping()
@CrossOrigin(origins = "*")
public class EventController {

    private final EventControlService eventService;
    private final SimulationEngineService engineService;
    private final AgentStateRegistry registry;

    /**
     * 构造函数注入
     *
     * @param eventService  事件控制服务
     * @param engineService 仿真引擎服务
     * @param registry      状态注册中心
     */
    public EventController(EventControlService eventService,
                           SimulationEngineService engineService,
                           AgentStateRegistry registry) {
        this.eventService = eventService;
        this.engineService = engineService;
        this.registry = registry;
    }

    /**
     * 触发组件故障
     *
     * @param name  组件名称
     * @param ticks 故障持续时间（tick数）
     * @return 200 OK
     */
    @PostMapping("/events/breakComponent")
    public ResponseEntity<String> breakComponent(@RequestParam String name,
                                                 @RequestParam int ticks) {
        eventService.addBrokenComponent(name, ticks);
        return ResponseEntity.ok(
                name + " 将故障 " + ticks + " 个 tick。");
    }

    /**
     * 触发负荷尖峰
     *
     * @param name  负荷名称
     * @param ticks 持续时间（tick数）
     * @param rate  能耗倍率
     * @return 200 OK
     */
    @PostMapping("/events/loadSpike")
    public ResponseEntity<String> spikeLoad(@RequestParam String name,
                                            @RequestParam int ticks,
                                            @RequestParam int rate) {
        eventService.addLoadSpike(name, ticks, rate);
        return ResponseEntity.ok(
                name + " 将尖峰 " + ticks + " 个 tick (倍率 " + rate + ")。");
    }

    /**
     * 触发停电事件
     *
     * @param ticks 停电持续时间（tick数）
     * @return 200 OK
     */
    @PostMapping("/events/blackout")
    public ResponseEntity<String> blackout(@RequestParam(defaultValue = "10") long ticks) {
        eventService.startBlackout(ticks);
        return ResponseEntity.ok("停电 " + ticks + " 个 tick。");
    }

    /**
     * 查询所有组件的状态
     *
     * @return 组件状态映射
     */
    @GetMapping("/events/componentStatus")
    public ResponseEntity<Map<String, ?>> componentStatus() {
        return ResponseEntity.ok(Map.of(
                "brokenComponents", eventService.getBrokenComponents(),
                "loadSpikes", eventService.getLoadSpikes(),
                "blackoutRemaining", eventService.getBlackoutRemaining()
        ));
    }
}