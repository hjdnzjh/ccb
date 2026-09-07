package com.water.ai.meter.controller;

import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.entity.WorkOrder;
import com.water.ai.meter.mapper.WorkOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "工单管理", description = "异常处置工单查询与流转")
@RestController
@RequestMapping("/api/v1/work-order")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderMapper workOrderMapper;

    @Operation(summary = "工单分页列表")
    @GetMapping("/list")
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String handlerName,
            @RequestParam(required = false) String meterNo) {
        long offset = Math.max(0, (long) (pageNum - 1) * pageSize);
        List<Map<String, Object>> records = workOrderMapper.selectJoinedPage(
                emptyToNull(orderNo), emptyToNull(orderType), status, priority,
                emptyToNull(handlerName), emptyToNull(meterNo), offset, pageSize);
        long total = workOrderMapper.countJoined(
                emptyToNull(orderNo), emptyToNull(orderType), status, priority,
                emptyToNull(handlerName), emptyToNull(meterNo));
        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return ApiResult.ok(data);
    }

    @Operation(summary = "工单状态统计")
    @GetMapping("/stats")
    public ApiResult<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        long total = 0;
        long pending = 0;
        long processing = 0;
        long done = 0;
        for (Map<String, Object> row : workOrderMapper.countByStatus()) {
            int st = ((Number) row.get("status")).intValue();
            long cnt = ((Number) row.get("cnt")).longValue();
            total += cnt;
            if (st == 0 || st == 1) pending += cnt;
            else if (st == 2) processing += cnt;
            else if (st == 3 || st == 4) done += cnt;
        }
        data.put("total", total);
        data.put("pending", pending);
        data.put("processing", processing);
        data.put("done", done);
        return ApiResult.ok(data);
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> row = workOrderMapper.selectJoinedById(id);
        if (row == null) {
            return ApiResult.fail("工单不存在");
        }
        return ApiResult.ok(row);
    }

    @Operation(summary = "派单")
    @PostMapping("/{id}/dispatch")
    public ApiResult<Void> dispatch(@PathVariable Long id, @RequestBody Map<String, String> body) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            return ApiResult.fail("工单不存在");
        }
        if (order.getStatus() != null && order.getStatus() > 1) {
            return ApiResult.fail("当前状态不可派单");
        }
        String handlerName = body.getOrDefault("handlerName", "").trim();
        if (!StringUtils.hasText(handlerName)) {
            return ApiResult.fail("请指定处理人");
        }
        order.setHandlerName(handlerName);
        order.setStatus(1);
        order.setDispatchTime(LocalDateTime.now());
        workOrderMapper.updateById(order);
        return ApiResult.ok("派单成功", null);
    }

    @Operation(summary = "接单处理中")
    @PostMapping("/{id}/accept")
    public ApiResult<Void> accept(@PathVariable Long id) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            return ApiResult.fail("工单不存在");
        }
        if (order.getStatus() == null || (order.getStatus() != 0 && order.getStatus() != 1)) {
            return ApiResult.fail("当前状态不可接单");
        }
        order.setStatus(2);
        order.setAcceptTime(LocalDateTime.now());
        if (order.getDispatchTime() == null) {
            order.setDispatchTime(LocalDateTime.now());
        }
        workOrderMapper.updateById(order);
        return ApiResult.ok("已转入处理中", null);
    }

    @Operation(summary = "完成工单")
    @PostMapping("/{id}/complete")
    public ApiResult<Void> complete(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            return ApiResult.fail("工单不存在");
        }
        if (order.getStatus() == null || order.getStatus() >= 3) {
            return ApiResult.fail("当前状态不可完成");
        }
        String result = body == null ? null : body.get("result");
        order.setStatus(3);
        order.setCompleteTime(LocalDateTime.now());
        if (StringUtils.hasText(result)) {
            order.setResult(result);
        }
        workOrderMapper.updateById(order);
        return ApiResult.ok("工单已完成", null);
    }

    @Operation(summary = "关闭工单")
    @PostMapping("/{id}/close")
    public ApiResult<Void> close(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            return ApiResult.fail("工单不存在");
        }
        String remark = body == null ? null : body.get("remark");
        order.setStatus(4);
        order.setCloseTime(LocalDateTime.now());
        if (StringUtils.hasText(remark)) {
            order.setRemark(remark);
        }
        workOrderMapper.updateById(order);
        return ApiResult.ok("工单已关闭", null);
    }

    private static String emptyToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }
}
