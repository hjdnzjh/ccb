package com.water.ai.meter.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.water.ai.meter.entity.MeterReading;
import com.water.ai.meter.service.MeterReadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 抄表管理接口
 */
@Tag(name = "抄表管理", description = "抄表相关接口")
@RestController
@RequestMapping("/api/v1/meter-reading")
@RequiredArgsConstructor
public class MeterReadingController {

    private final MeterReadingService meterReadingService;

    @Operation(summary = "AI图像抄表", description = "上传水表图片，AI自动识别读数")
    @PostMapping("/ai-image")
    public Map<String, Object> aiImageReading(
            @Parameter(description = "水表ID") @RequestParam Long meterId,
            @Parameter(description = "图片Base64") @RequestBody String imageData) {
        return meterReadingService.aiImageReading(meterId, imageData);
    }

    @Operation(summary = "远程抄表", description = "远程读取智能水表数据")
    @PostMapping("/remote/{meterId}")
    public Map<String, Object> remoteReading(
            @Parameter(description = "水表ID") @PathVariable Long meterId) {
        return meterReadingService.remoteReading(meterId);
    }

    @Operation(summary = "批量抄表", description = "批量执行抄表任务")
    @PostMapping("/batch")
    public Map<String, Object> batchReading(
            @Parameter(description = "区域ID") @RequestParam(required = false) Long areaId,
            @Parameter(description = "预计数量") @RequestParam(defaultValue = "100") Integer meterCount) {
        return meterReadingService.batchReading(areaId, meterCount);
    }

    @Operation(summary = "人工录入", description = "人工录入抄表数据")
    @PostMapping("/manual")
    public Map<String, Object> manualReading(
            @Parameter(description = "水表ID") @RequestParam Long meterId,
            @Parameter(description = "读数") @RequestParam BigDecimal reading,
            @Parameter(description = "操作人") @RequestParam String operator) {
        return meterReadingService.manualReading(meterId, reading, operator);
    }

    @Operation(summary = "审核抄表", description = "人工审核抄表记录")
    @PostMapping("/review/{readingId}")
    public Map<String, Object> reviewReading(
            @Parameter(description = "抄表记录ID") @PathVariable Long readingId,
            @Parameter(description = "审核人") @RequestParam String reviewer,
            @Parameter(description = "是否通过") @RequestParam boolean approved,
            @Parameter(description = "原因") @RequestParam(required = false) String reason) {
        return meterReadingService.reviewReading(readingId, reviewer, approved, reason);
    }

    @Operation(summary = "抄表历史", description = "查询水表抄表历史记录")
    @GetMapping("/history/{meterId}")
    public List<MeterReading> getHistory(
            @Parameter(description = "水表ID") @PathVariable Long meterId,
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "10") int limit) {
        return meterReadingService.getReadingHistory(meterId, limit);
    }

    @Operation(summary = "待审核列表", description = "分页查询待审核抄表记录")
    @GetMapping("/pending-review")
    public IPage<MeterReading> getPendingReview(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {
        return meterReadingService.getPendingReviewList(new Page<>(pageNum, pageSize));
    }

    @Operation(summary = "抄表统计", description = "获取抄表统计数据")
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics(
            @Parameter(description = "开始日期 yyyy-MM-dd") @RequestParam String startDate) {
        return meterReadingService.getReadingStatistics(startDate);
    }
}