package com.water.ai.meter.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抄表记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("meter_reading")
@Accessors(chain = true)
public class MeterReading implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 水表ID
     */
    private Long meterId;

    /**
     * 水表编号
     */
    private String meterNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 读数
     */
    private BigDecimal readingValue;

    /**
     * 用水量(本次-上次)
     */
    private BigDecimal usageAmount;

    /**
     * 抄表方式: ai_image-AI图像, remote-远程, manual-人工
     */
    private String readingType;

    /**
     * AI识别置信度
     */
    private BigDecimal confidence;

    /**
     * 水表图片URL
     */
    private String imageUrl;

    /**
     * 状态: 0-待审核, 1-已确认, 2-异常
     */
    private Integer status;

    /**
     * 审核人
     */
    private String reviewer;

    /**
     * 审核时间
     */
    private LocalDateTime reviewTime;

    /**
     * 抄表时间
     */
    private LocalDateTime readingTime;

    /**
     * 抄表周期
     */
    private String readingPeriod;

    /**
     * AI识别原始结果(JSON)
     */
    private String aiResult;

    /**
     * 异常标记: 0-正常, 1-异常
     */
    private Integer anomalyFlag;

    /**
     * 异常类型
     */
    private String anomalyType;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}