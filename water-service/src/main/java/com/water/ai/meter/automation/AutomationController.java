package com.water.ai.meter.automation;

import com.water.ai.meter.common.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/automation")
@RequiredArgsConstructor
public class AutomationController {
    private final CollectionService collection;
    private final PenaltyService penalty;
    private final AutonomousCoverageService coverage;
    @GetMapping("/coverage-policy") public ApiResult<?> coverage(){return ApiResult.ok(coverage.policy());}
    @PutMapping("/coverage-policy") public ApiResult<?> coverage(@RequestBody AutonomousCoverageService.Input input){coverage.save(input);return ApiResult.ok(coverage.policy());}
    @GetMapping("/plans") public ApiResult<?> plans() { return ApiResult.ok(collection.plans()); }
    @GetMapping("/meters") public ApiResult<?> meters() { return ApiResult.ok(collection.meters()); }
    @PostMapping("/plans") public ApiResult<?> create(@RequestBody CollectionService.PlanInput input) { return ApiResult.ok(Map.of("id",collection.createPlan(input))); }
    public record Enabled(boolean enabled) {}
    @PutMapping("/plans/{id}/enabled") public ApiResult<?> enabled(@PathVariable long id,@RequestBody Enabled input) { collection.enabled(id,input.enabled()); return ApiResult.ok("已更新",null); }
    public record Trigger(String requestKey) {}
    @PostMapping("/plans/{id}/trigger") public ApiResult<?> trigger(@PathVariable long id,@RequestBody Trigger input) { return ApiResult.ok(Map.of("runId",collection.trigger(id,input.requestKey()))); }
    @GetMapping("/runs") public ApiResult<?> runs() { return ApiResult.ok(collection.runs()); }
    @GetMapping("/runs/{id}/tasks") public ApiResult<?> tasks(@PathVariable long id) { return ApiResult.ok(collection.tasks(id)); }
    public record Packet(String packet) {}
    @PostMapping("/simulated-report") public ApiResult<?> report(@RequestBody Packet input) { return ApiResult.ok(Map.of("readingId",collection.ingest(input.packet()),"source","simulated")); }
    @PostMapping("/process") public ApiResult<?> process() { collection.tick(); return ApiResult.ok("已运行到期计划与补抄",null); }
    @GetMapping("/penalty-policy") public ApiResult<?> policy() { return ApiResult.ok(penalty.policy()); }
    @PutMapping("/penalty-policy") public ApiResult<?> policy(@RequestBody PenaltyService.PolicyInput input) { penalty.savePolicy(input); return ApiResult.ok(penalty.policy()); }
    @GetMapping("/penalty-ledger") public ApiResult<?> ledger() { return ApiResult.ok(penalty.ledger()); }
    @PostMapping("/penalties/run") public ApiResult<?> accrue() { return ApiResult.ok(Map.of("daysProcessed",penalty.accrue())); }
}
