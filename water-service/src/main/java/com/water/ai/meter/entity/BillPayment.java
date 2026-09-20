package com.water.ai.meter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable receipt registration; this is not a payment gateway transaction. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("bill_payment")
public class BillPayment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long billId;
    private BigDecimal amount;
    private String payMethod;
    private String tradeNo;
    private LocalDateTime paidTime;
}
