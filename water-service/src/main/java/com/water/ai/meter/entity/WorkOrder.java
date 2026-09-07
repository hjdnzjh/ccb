package com.water.ai.meter.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("work_order")
@Accessors(chain = true)
public class WorkOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 工单编号
     */
    private String orderNo;

    /**
     * 工单类型: emergency-紧急, urgent-加急, normal-普通
     */
    private String orderType;

    /**
     * 异常记录ID
     */
    private Long anomalyId;

    /**
     * 水表ID
     */
    private Long meterId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 区域ID
     */
    private Long areaId;

    /**
     * 工单标题
     */
    private String title;

    /**
     * 工单描述
     */
    private String description;

    /**
     * 处理人ID
     */
    private Long handlerId;

    /**
     * 处理人姓名
     */
    private String handlerName;

    /**
     * 优先级: 1-紧急, 2-高, 3-普通
     */
    private Integer priority;

    /**
     * 状态: 0-待派单, 1-已派单, 2-处理中, 3-已完成, 4-已关闭
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 派单时间
     */
    private LocalDateTime dispatchTime;

    /**
     * 接单时间
     */
    private LocalDateTime acceptTime;

    /**
     * 完成时间
     */
    private LocalDateTime completeTime;

    /**
     * 关闭时间
     */
    private LocalDateTime closeTime;

    /**
     * 处理结果
     */
    private String result;

    /**
     * 处理图片(JSON数组)
     */
    private String images;

    /**
     * 评价: 1-5星
     */
    private Integer rating;

    /**
     * 评价内容
     */
    private String feedback;

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