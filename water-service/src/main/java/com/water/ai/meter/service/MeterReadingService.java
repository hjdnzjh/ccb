package com.water.ai.meter.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.water.ai.meter.entity.MeterReading;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 抄表服务接口
 */
public interface MeterReadingService extends IService<MeterReading> {

    /**
     * AI图像抄表
     * @param meterId 水表ID
     * @param imageData 图片数据(Base64)
     * @return 抄表结果
     */
    Map<String, Object> aiImageReading(Long meterId, String imageData);

    /**
     * 远程抄表
     * @param meterId 水表ID
     * @return 抄表结果
     */
    Map<String, Object> remoteReading(Long meterId);

    /**
     * 批量抄表
     * @param areaId 区域ID(可选)
     * @param meterCount 预计水表数量
     * @return 批量抄表结果
     */
    Map<String, Object> batchReading(Long areaId, Integer meterCount);

    /**
     * 人工录入抄表
     * @param meterId 水表ID
     * @param reading 读数
     * @param operator 操作人
     * @return 录入结果
     */
    Map<String, Object> manualReading(Long meterId, BigDecimal reading, String operator);

    /**
     * 审核抄表记录
     * @param readingId 抄表记录ID
     * @param reviewer 审核人
     * @param approved 是否通过
     * @param reason 原因(不通过时)
     * @return 审核结果
     */
    Map<String, Object> reviewReading(Long readingId, String reviewer, boolean approved, String reason);

    /**
     * 查询抄表历史
     * @param meterId 水表ID
     * @param limit 数量限制
     * @return 历史记录列表
     */
    List<MeterReading> getReadingHistory(Long meterId, int limit);

    /**
     * 分页查询待审核记录
     * @param page 分页参数
     * @return 分页结果
     */
    IPage<MeterReading> getPendingReviewList(Page<MeterReading> page);

    /**
     * 获取抄表统计数据
     * @param startDate 开始日期
     * @return 统计数据
     */
    Map<String, Object> getReadingStatistics(String startDate);

    /**
     * 调用智能体引擎执行抄表
     * @param context 抄表上下文
     * @return 智能体执行结果
     */
    Map<String, Object> callAgentEngine(Map<String, Object> context);
}