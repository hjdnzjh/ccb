package com.water.ai.meter.controller;

import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.entity.Bill;
import com.water.ai.meter.mapper.BillMapper;
import com.water.ai.meter.service.BillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 账单管理接口
 */
@Tag(name = "账单管理", description = "账单生成、查询、缴费相关接口")
@RestController
@RequestMapping("/api/v1/bill")
@RequiredArgsConstructor
public class BillingController {

    private final BillService billService;
    private final BillMapper billMapper;

    @Operation(summary = "管理端账单分页列表")
    @GetMapping("/list")
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String billPeriod) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        long offset = (long) (safePageNum - 1) * safePageSize;
        String safeBillNo = emptyToNull(billNo);
        String safeUserName = emptyToNull(userName);
        String safeBillPeriod = emptyToNull(billPeriod);

        List<Map<String, Object>> records = billMapper.selectAdminPage(
                safeBillNo, safeUserName, status, safeBillPeriod, offset, safePageSize);
        long total = billMapper.countAdminPage(safeBillNo, safeUserName, status, safeBillPeriod);
        Map<String, Object> stats = billMapper.sumAdminPage(safeBillNo, safeUserName, status, safeBillPeriod);

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("pageNum", safePageNum);
        data.put("pageSize", safePageSize);
        data.put("stats", stats == null ? Map.of() : stats);
        return ApiResult.ok(data);
    }

    @Operation(summary = "生成账单", description = "根据抄表记录生成账单")
    @PostMapping("/generate")
    public Map<String, Object> generateBill(
            @Parameter(description = "水表ID") @RequestParam Long meterId,
            @Parameter(description = "抄表记录ID") @RequestParam Long readingId) {
        return billService.generateBill(meterId, readingId);
    }

    @Operation(summary = "批量生成账单", description = "批量生成账单")
    @PostMapping("/batch-generate")
    public Map<String, Object> batchGenerate(
            @Parameter(description = "抄表记录ID列表") @RequestBody List<Long> readingIds) {
        return billService.batchGenerateBills(readingIds);
    }

    @Operation(summary = "查询用户账单", description = "查询指定用户的账单列表")
    @GetMapping("/user/{userId}")
    public List<Bill> getUserBills(
            @Parameter(description = "用户ID") @PathVariable Long userId) {
        return billService.getBillsByUserId(userId);
    }

    @Operation(summary = "查询未支付账单", description = "查询用户未支付账单")
    @GetMapping("/unpaid/{userId}")
    public List<Bill> getUnpaidBills(
            @Parameter(description = "用户ID") @PathVariable Long userId) {
        return billService.getUnpaidBills(userId);
    }

    @Operation(summary = "账单详情", description = "查询账单详情")
    @GetMapping("/{billId}")
    public Bill getBillDetail(
            @Parameter(description = "账单ID") @PathVariable Long billId) {
        return billService.getById(billId);
    }

    @Operation(summary = "缴费", description = "账单缴费")
    @PostMapping("/pay/{billId}")
    public Map<String, Object> payBill(
            @Parameter(description = "账单ID") @PathVariable Long billId,
            @Parameter(description = "支付金额") @RequestParam BigDecimal amount,
            @Parameter(description = "支付方式") @RequestParam String payMethod,
            @Parameter(description = "交易号") @RequestParam(required = false) String tradeNo) {
        return billService.payBill(billId, amount, payMethod, tradeNo);
    }

    @Operation(summary = "欠费账单", description = "查询所有逾期账单")
    @GetMapping("/overdue")
    public List<Bill> getOverdueBills() {
        return billService.getOverdueBills();
    }

    @Operation(summary = "欠费排行", description = "欠费金额排行")
    @GetMapping("/overdue-rank")
    public List<Map<String, Object>> getOverdueRank(
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "10") int limit) {
        return billService.getTopOverdueUsers(limit);
    }

    @Operation(summary = "账单统计", description = "账单统计数据")
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics(
            @Parameter(description = "开始日期 yyyy-MM-dd") @RequestParam String startDate) {
        return billService.getBillStatistics(startDate);
    }

    @Operation(summary = "收入统计", description = "收入汇总统计")
    @GetMapping("/revenue")
    public Map<String, Object> getRevenueStatistics(
            @Parameter(description = "开始日期") @RequestParam String startDate,
            @Parameter(description = "结束日期") @RequestParam String endDate) {
        return billService.getRevenueStatistics(startDate, endDate);
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
