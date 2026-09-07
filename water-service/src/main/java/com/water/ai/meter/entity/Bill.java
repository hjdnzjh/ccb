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
 * 账单实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("bill")
@Accessors(chain = true)
public class Bill implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账单编号
     */
    private String billNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 水表ID
     */
    private Long meterId;

    /**
     * 抄表记录ID
     */
    private Long readingId;

    /**
     * 账期
     */
    private String billPeriod;

    /**
     * 起始读数
     */
    private BigDecimal startReading;

    /**
     * 结束读数
     */
    private BigDecimal endReading;

    /**
     * 用水量
     */
    private BigDecimal usageAmount;

    /**
     * 水费
     */
    private BigDecimal waterFee;

    /**
     * 污水处理费
     */
    private BigDecimal sewageFee;

    /**
     * 滞纳金
     */
    private BigDecimal penalty;

    /**
     * 优惠金额
     */
    private BigDecimal discount;

    /**
     * 应缴金额
     */
    private BigDecimal totalAmount;

    /**
     * 实缴金额
     */
    private BigDecimal paidAmount;

    /**
     * 费率类型: residential-居民, commercial-商业, industrial-工业
     */
    private String priceType;

    /**
     * 阶梯明细(JSON)
     */
    private String ladderDetail;

    /**
     * 账单状态: 0-未支付, 1-已支付, 2-已逾期, 3-部分支付
     */
    private Integer status;

    /**
     * 应缴日期
     */
    private LocalDateTime dueDate;

    /**
     * 支付时间
     */
    private LocalDateTime paidTime;

    /**
     * 支付方式: wechat-微信, alipay-支付宝, bank-银行, cash-现金
     */
    private String payMethod;

    /**
     * 支付流水号
     */
    private String tradeNo;

    /**
     * 打印次数
     */
    private Integer printCount;

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