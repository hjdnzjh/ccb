package com.water.ai.meter.controller;

import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.service.OpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "智能运营", description = "驾驶舱/孪生/健康度/助手等决策接口")
@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
public class OpsController {

    private final OpsService opsService;

    @Operation(summary = "AI运营驾驶舱")
    @GetMapping("/cockpit")
    public ApiResult<Map<String, Object>> cockpit() {
        return ApiResult.ok(opsService.cockpit());
    }

    @Operation(summary = "数字孪生分区")
    @GetMapping("/twin/zones")
    public ApiResult<List<Map<String, Object>>> twinZones() {
        return ApiResult.ok(opsService.twinZones());
    }

    @Operation(summary = "分区详情")
    @GetMapping("/twin/zones/{areaCode}")
    public ApiResult<Map<String, Object>> zoneDetail(@PathVariable String areaCode) {
        return ApiResult.ok(opsService.zoneDetail(areaCode));
    }

    @Operation(summary = "杭州孪生地图点位")
    @GetMapping("/twin/map")
    public ApiResult<Map<String, Object>> twinMap() {
        return ApiResult.ok(opsService.twinMap());
    }

    @Operation(summary = "水表健康指数")
    @GetMapping("/meter-health")
    public ApiResult<List<Map<String, Object>>> meterHealth() {
        return ApiResult.ok(opsService.meterHealth());
    }

    @Operation(summary = "AI抄表可信度")
    @GetMapping("/ocr-trust")
    public ApiResult<List<Map<String, Object>>> ocrTrust(
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.ok(opsService.ocrTrust(limit));
    }

    @Operation(summary = "智能收费洞察")
    @GetMapping("/billing-insights")
    public ApiResult<List<Map<String, Object>>> billingInsights() {
        return ApiResult.ok(opsService.billingInsights());
    }

    @Operation(summary = "水务智能助手")
    @PostMapping("/assistant")
    public ApiResult<Map<String, Object>> assistant(@RequestBody Map<String, String> body) {
        return ApiResult.ok(opsService.assistant(body.getOrDefault("question", "")));
    }

    @Operation(summary = "最近抄表")
    @GetMapping("/readings/recent")
    public ApiResult<List<Map<String, Object>>> recentReadings(
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(opsService.recentReadings(limit));
    }
}
