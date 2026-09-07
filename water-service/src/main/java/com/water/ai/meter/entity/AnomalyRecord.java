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
 * 异常记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("anomaly_record")
@Accessors(chain = true)
public class AnomalyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 异常ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 异常编号
     */
    private String anomalyNo;

    /**
     * 水表ID
     */
    private Long meterId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 异常类型: leak-漏水, theft-偷水, meter_fault-表故障, 
     *          sudden_increase-突增, zero_usage-零用量, night_usage-夜间异常
     */
    private String anomalyType;

    /**
     * 严重程度: critical-紧急, high-高, medium-中, low-低
     */
    private String severity;

    /**
     * AI评分(0-1)
     */
    private BigDecimal aiScore;

    /**
     * 异常描述
     */
    private String description;

    /**
     * 检测详情(JSON)
     */
    private String detectionDetail;

    /**
     * 状态: 0-待处理, 1-处理中, 2-已处理, 3-已关闭
     */
    private Integer status;

    /**
     * 检测时间
     */
    private LocalDateTime detectedTime;

    /**
     * 处理时间
     */
    private LocalDateTime handledTime;

    /**
     * 处理人
     */
    private String handler;

    /**
     * 处理结果
     */
    private String handleResult;

    /**
     * 工单ID
     */
    private Long workOrderId;

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

    /**
     * 备注
     */
    private String remark;
}