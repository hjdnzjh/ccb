package com.water.ai.meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.water.ai.meter.entity.BillPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface BillPaymentMapper extends BaseMapper<BillPayment> {
    @Select("SELECT * FROM bill_payment WHERE trade_no = #{tradeNo}")
    BillPayment selectByTradeNo(String tradeNo);

    @Select("SELECT * FROM bill_payment WHERE bill_id = #{billId} ORDER BY id DESC")
    List<BillPayment> selectByBillId(Long billId);
}
