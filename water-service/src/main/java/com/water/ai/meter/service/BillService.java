package com.water.ai.meter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.water.ai.meter.entity.Bill;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 账单服务接口
 */
public interface BillService extends IService<Bill> {

    /**
     * 生成账单
     */
    Map<String, Object> generateBill(Long meterId, Long readingId);

    /**
     * 批量生成账单
     */
    Map<String, Object> batchGenerateBills(List<Long> readingIds);

    /**
     * 获取用户账单列表
     */
    List<Bill> getBillsByUserId(Long userId);

    /**
     * 获取未支付账单
     */
    List<Bill> getUnpaidBills(Long userId);

    /**
     * 缴费
     */
    Map<String, Object> payBill(Long billId, BigDecimal amount, String payMethod, String tradeNo);

    /**
     * 获取逾期账单
     */
    List<Bill> getOverdueBills();

    /**
     * 欠费排行
     */
    List<Map<String, Object>> getTopOverdueUsers(int limit);

    /**
     * 账单统计
     */
    Map<String, Object> getBillStatistics(String startDate);

    /**
     * 收入统计
     */
    Map<String, Object> getRevenueStatistics(String startDate, String endDate);
}