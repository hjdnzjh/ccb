package com.water.ai.meter.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.entity.WaterMeter;
import com.water.ai.meter.mapper.WaterMeterMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "水表资产", description = "水表查询")
@RestController
@RequestMapping("/api/v1/meter")
@RequiredArgsConstructor
public class MeterController {

    private final WaterMeterMapper waterMeterMapper;

    @Operation(summary = "水表分页列表")
    @GetMapping("/list")
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String meterNo,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<WaterMeter> qw = new LambdaQueryWrapper<WaterMeter>()
                .eq(WaterMeter::getDeleted, 0)
                .like(StringUtils.hasText(meterNo), WaterMeter::getMeterNo, meterNo)
                .eq(status != null, WaterMeter::getStatus, status)
                .orderByDesc(WaterMeter::getUpdateTime);
        Page<WaterMeter> page = waterMeterMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        Map<String, Object> data = new HashMap<>();
        data.put("records", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        return ApiResult.ok(data);
    }

    @Operation(summary = "水表详情")
    @GetMapping("/{id}")
    public ApiResult<WaterMeter> detail(@PathVariable Long id) {
        return ApiResult.ok(waterMeterMapper.selectById(id));
    }
}
