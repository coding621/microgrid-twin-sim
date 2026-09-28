package com.microgrid.sim.controller;

import com.microgrid.sim.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 仿真控制器 - REST API 入口，管理仿真的启动/停止/暂停/恢复/加速。
 *
 * <p>提供以下端点：
 * <ul>
 *   <li>POST /api/simulation/loadConfig  - 加载 JSON 配置文件</li>
 *   <li>POST /api/simulation/start        - 启动仿真</li>
 *   <li>POST /api/simulation/stop         - 停止仿真</li>
 *   <li>POST /api/simulation/pause        - 暂停仿真</li>
 *   <li>POST /api/simulation/resume       - 恢复仿真</li>
 *   <li>POST /api/simulation/speedUp      - 设置加速因子</li>
 *   <li>GET  /api/simulation/status       - 查询仿真状态</li>
 *   <li>GET  /api/logs                    - 查询所有仿真日志</li>
 *   <li>GET  /api/logs/{agentName}        - 查询指定智能体的日志</li>
 * </ul>
 *
 *
 * @author Coding
 */
@RestController
@RequestMapping()
@CrossOrigin(origins = "*")
public class SimulationController {

    private final SimulationConfigService configService;
    private final SimulationEngineService engineService;
    private final LogAggregatorService logAggregator;

    /**
     * 构造函数注入
     *
     * @param configService 配置服务
     * @param engineService 仿真引擎服务
     * @param logAggregator 日志服务
     */
    public SimulationController(SimulationConfigService configService,
                                SimulationEngineService engineService,
                                LogAggregatorService logAggregator) {
        this.configService = configService;
        this.engineService = engineService;
        this.logAggregator = logAggregator;
    }

    /**
     * 加载仿真配置 - 接收 JSON 配置并解析。
     *
     * @param payload JSON 配置字符串
     * @return 200 OK 如果加载成功
     */
    @PostMapping("/simulation/loadConfig")
    public ResponseEntity<String> loadConfig(@RequestBody String payload) {
        try {
            configService.setConfigFromString(payload);
            return ResponseEntity.ok("配置加载成功。");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("配置加载失败: " + e.getMessage());
        }
    }

    /**
     * 启动仿真 - 接收前端传来的 JSON 配置并加载后启动。
     *
     * <p>前端会将全部仿真实体的参数以 JSON 形式发送到此端点，
     * 包括实体的 name 字段。后端使用这些 name 作为 agentStates 的 key，
     * 确保 TickData 中的 key 与前端 entitiesStore 中的 id 完全一致。
     *
     * @param payload JSON 配置字符串（由前端 SimulationDrawer 生成）
     * @return 200 OK 或 409 Conflict 如果在运行中
     */
    @PostMapping("/simulation/start")
    public ResponseEntity<String> start(@RequestBody String payload) {
        try {
            configService.setConfigFromString(payload);
            engineService.start();
            return ResponseEntity.ok("仿真启动成功。");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("仿真启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止仿真
     *
     * @return 200 OK
     */
    @PostMapping("/simulation/stop")
    public ResponseEntity<String> stop() {
        engineService.stop();
        return ResponseEntity.ok("仿真已停止。");
    }

    /**
     * 暂停仿真
     *
     * @return 200 OK
     */
    @PostMapping("/simulation/pause")
    public ResponseEntity<String> pause() {
        engineService.pause();
        return ResponseEntity.ok("仿真已暂停。");
    }

    /**
     * 恢复仿真
     *
     * @return 200 OK
     */
    @PostMapping("/simulation/resume")
    public ResponseEntity<String> resume() {
        engineService.resume();
        return ResponseEntity.ok("仿真已恢复。");
    }

    /**
     * 设置加速因子
     *
     * @param factor 加速倍数 (>0)
     * @return 200 OK
     */
    @PostMapping("/simulation/speedUp")
    public ResponseEntity<String> setSpeedUpFactor(@RequestParam double factor) {
        if (factor <= 0) {
            return ResponseEntity.badRequest().body("加速因子必须大于 0。");
        }
        engineService.setSpeedUpFactor(factor);
        return ResponseEntity.ok("加速因子已设置为 " + factor + "。");
    }

    /**
     * 查询仿真运行状态
     *
     * @return 状态信息
     */
    @GetMapping("/simulation/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "running", engineService.isRunning(),
                "paused", engineService.isPaused(),
                "currentTick", engineService.getCurrentTick()
        ));
    }

    /**
     * 查询所有仿真日志
     *
     * @return 按智能体分组的日志
     */
    @GetMapping("/logs")
    public ResponseEntity<Map<String, List<String>>> allLogs() {
        return ResponseEntity.ok(logAggregator.getAllLogs());
    }

    /**
     * 查询指定智能体的仿真日志
     *
     * @param agentName 智能体名称
     * @return 日志列表
     */
    @GetMapping("/logs/{agentName}")
    public ResponseEntity<List<String>> agentLogs(@PathVariable String agentName) {
        return ResponseEntity.ok(logAggregator.getLogsForAgent(agentName));
    }
}