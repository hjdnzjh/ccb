package com.water.ai.meter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.water.ai.meter.entity.Bill;
import com.water.ai.meter.entity.BillPayment;
import com.water.ai.meter.mapper.BillPaymentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.water.ai.meter.entity.MeterReading;
import com.water.ai.meter.entity.SysUser;
import com.water.ai.meter.entity.WaterMeter;
import com.water.ai.meter.mapper.BillMapper;
import com.water.ai.meter.mapper.MeterReadingMapper;
import com.water.ai.meter.mapper.SysUserMapper;
import com.water.ai.meter.mapper.WaterMeterMapper;
import com.water.ai.meter.service.BillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 账单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillServiceImpl extends ServiceImpl<BillMapper, Bill> implements BillService {

    private final WaterMeterMapper waterMeterMapper;
    private final SysUserMapper sysUserMapper;
    private final MeterReadingMapper meterReadingMapper;
    private final BillPaymentMapper billPaymentMapper;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;
    private final com.water.ai.meter.tariff.ResidentialTariffService residentialTariff;

    // 阶梯水价配置
    @Value("${water-meter.pricing.residential.ladder1:180}")
    private int ladder1;

    @Value("${water-meter.pricing.residential.price1:2.07}")
    private BigDecimal price1;

    @Value("${water-meter.pricing.residential.ladder2:260}")
    private int ladder2;

    @Value("${water-meter.pricing.residential.price2:3.10}")
    private BigDecimal price2;

    @Value("${water-meter.pricing.residential.price3:4.65}")
    private BigDecimal price3;

    @Value("${water-meter.pricing.commercial:4.50}")
    private BigDecimal commercialPrice;

    @Value("${water-meter.pricing.industrial:5.80}")
    private BigDecimal industrialPrice;

    @Value("${water-meter.sewage-rate:0.9}")
    private BigDecimal sewageRate;

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> generateBill(Long meterId, Long readingId) {
        log.info("生成账单: meterId={}, readingId={}", meterId, readingId);

        // 获取水表和抄表信息
        if (meterId == null || readingId == null) return errorResult("水表和抄表 ID 不能为空");
        MeterReading reading = meterReadingMapper.selectForUpdate(readingId);
        WaterMeter meter = waterMeterMapper.selectById(meterId);
        
        if (meter == null || reading == null) {
            return errorResult("水表或抄表记录不存在");
        }
        if (!Objects.equals(reading.getMeterId(), meterId) || meter.getUserId() == null
                || !Objects.equals(reading.getUserId(), meter.getUserId())) {
            return errorResult("抄表记录与水表或用户不匹配");
        }
        if (!Integer.valueOf(1).equals(reading.getStatus())) return errorResult("仅已确认的抄表记录可以出账");
        // Serialize a shared allowance before the bill existence query takes any locks.
        residentialTariff.lockForBilling(meterId);
        List<Bill> existing = baseMapper.selectByReadingId(readingId);
        if (existing.size() > 1) return errorResult("该抄表已有重复账单，请人工核对");
        if (!existing.isEmpty()) return generatedResult(existing.get(0), true);

        // 获取用户信息
        SysUser user = sysUserMapper.selectById(meter.getUserId());
        if (user == null) {
            return errorResult("用户不存在");
        }
        if (!Set.of("residential", "commercial", "industrial").contains(
                Objects.toString(user.getUserType(), ""))) return errorResult("不支持的用户费率类型");

        // 计算用水量
        BigDecimal usage = reading.getUsageAmount();
        if (usage == null || usage.signum() < 0 || reading.getReadingValue() == null
                || reading.getReadingValue().compareTo(usage) < 0) {
            return errorResult("抄表用量或累计读数无效");
        }
        String period = reading.getReadingPeriod();
        if (period == null || period.isBlank()) {
            if (reading.getReadingTime() == null) return errorResult("抄表时间和账期均为空");
            period = reading.getReadingTime().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        if (!period.matches("\\d{4}-(0[1-9]|1[0-2])")) return errorResult("抄表账期格式无效");

        // 计算费用
        var annual = residentialTariff.prepare(meterId,user.getId(),user.getUserType(),reading);
        Map<String, Object> feeResult = annual==null ? calculateFee(usage, user.getUserType()) : Map.of(
                "waterFee",annual.quote().water(),"sewageFee",annual.quote().sewage(),"ladderDetail",annual.detail());
        BigDecimal waterFee = (BigDecimal) feeResult.get("waterFee");
        BigDecimal sewageFee = (BigDecimal) feeResult.get("sewageFee");
        BigDecimal totalAmount = waterFee.add(sewageFee);
        if (totalAmount.compareTo(new BigDecimal("9999999999.99")) > 0) return errorResult("账单金额超出范围");

        // 生成账单编号
        String billNo = generateBillNo();

        // 创建账单
        Bill bill = Bill.builder()
                .billNo(billNo)
                .userId(user.getId())
                .meterId(meterId)
                .readingId(readingId)
                .billPeriod(period)
                .startReading(reading.getReadingValue().subtract(usage))
                .endReading(reading.getReadingValue())
                .usageAmount(usage)
                .waterFee(waterFee)
                .sewageFee(sewageFee)
                .totalAmount(totalAmount)
                .priceType(user.getUserType())
                .ladderDetail(toJson(feeResult.get("ladderDetail")))
                .paidAmount(BigDecimal.ZERO)
                .penalty(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .status(totalAmount.signum() == 0 ? 1 : 0)
                .deleted(0)
                .dueDate(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(15))
                .build();

        if (!this.save(bill)) throw new IllegalStateException("账单保存失败");
        if(annual!=null)residentialTariff.record(annual,bill.getId(),readingId);

        log.info("账单生成成功: billNo={}, amount={}", billNo, totalAmount);

        return generatedResult(bill, false);
    }

    @Override
    public Map<String, Object> batchGenerateBills(List<Long> readingIds) {
        if (readingIds == null || readingIds.isEmpty() || readingIds.size() > 200) {
            return errorResult("每次批量出账需包含 1 至 200 条抄表记录");
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        int createdCount = 0;
        int existingCount = 0;
        List<String> errors = new ArrayList<>();

        for (Long readingId : readingIds) {
            try {
                Map<String, Object> result = transaction.execute(status -> {
                    MeterReading reading = readingId == null ? null : meterReadingMapper.selectForUpdate(readingId);
                    return reading == null ? errorResult("抄表记录不存在") : generateBill(reading.getMeterId(), readingId);
                });
                if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                    Map<?, ?> data = (Map<?, ?>) result.get("data");
                    if (Boolean.TRUE.equals(data.get("existing"))) existingCount++;
                    else createdCount++;
                } else {
                    errors.add("readingId=" + readingId + ": " + (result == null ? "出账失败" : result.get("message")));
                }
            } catch (Exception e) {
                errors.add("readingId=" + readingId + ": " + e.getMessage());
            }
        }

        return successResult(Map.of(
                "total", readingIds.size(),
                "success", createdCount + existingCount,
                "created", createdCount,
                "existing", existingCount,
                "failed", errors.size(),
                "errors", errors
        ));
    }

    @Override
    public List<Bill> getBillsByUserId(Long userId) {
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public List<Bill> getUnpaidBills(Long userId) {
        return baseMapper.selectUnpaidByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payBill(Long billId, BigDecimal amount, String payMethod, String tradeNo) {
        log.info("账单缴费: billId={}, amount={}, payMethod={}", billId, amount, payMethod);

        if (billId == null || amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            return errorResult("缴费金额必须为正数且最多两位小数");
        }
        if (payMethod == null || !Set.of("wechat", "alipay", "bank", "cash").contains(payMethod)) {
            return errorResult("支付方式无效");
        }
        if (tradeNo == null || !tradeNo.matches("[A-Za-z0-9_:-]{1,100}")) {
            return errorResult("请提供有效的收款请求号");
        }
        Bill bill = baseMapper.selectForUpdate(billId);
        if (bill == null) {
            return errorResult("账单不存在");
        }

        BillPayment existing = billPaymentMapper.selectByTradeNo(tradeNo);
        if (existing != null) {
            if (!Objects.equals(existing.getBillId(), billId) || existing.getAmount().compareTo(amount) != 0
                    || !existing.getPayMethod().equals(payMethod)) return errorResult("收款请求号已被其他登记使用");
            return paymentResult(bill, existing, true);
        }
        BigDecimal paid = bill.getPaidAmount() == null ? BigDecimal.ZERO : bill.getPaidAmount();
        BigDecimal total = bill.getTotalAmount();
        if (total == null || total.signum() < 0 || paid.signum() < 0 || paid.compareTo(total) > 0) {
            return errorResult("账单金额数据异常，请人工核对");
        }
        if (bill.getStatus() == null || !Set.of(0, 2, 3).contains(bill.getStatus())
                || total.compareTo(paid) == 0) return errorResult("账单不可继续缴费");
        if (amount.compareTo(total.subtract(paid)) > 0) return errorResult("缴费金额不能超过剩余应付");

        BillPayment payment = BillPayment.builder().billId(billId).amount(amount.setScale(2))
                .payMethod(payMethod).tradeNo(tradeNo).paidTime(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))).build();
        // Unique request key is enforced by MySQL as well, including concurrent requests on different bills.
        if (billPaymentMapper.insert(payment) != 1) throw new IllegalStateException("收款流水保存失败");
        bill.setPaidAmount(paid.add(amount).setScale(2));
        bill.setStatus(bill.getPaidAmount().compareTo(total) == 0 ? 1 : 3);
        bill.setPaidTime(payment.getPaidTime());
        bill.setPayMethod(payMethod);
        bill.setTradeNo(tradeNo);
        if (baseMapper.updatePayment(billId, bill.getStatus(), bill.getPaidAmount(), bill.getPaidTime(),
                payMethod, tradeNo) != 1) {
            throw new IllegalStateException("账单收款更新失败");
        }
        return paymentResult(bill, payment, false);
    }

    @Override
    public List<Bill> getOverdueBills() {
        return baseMapper.selectOverdueBills();
    }

    @Override
    public List<Map<String, Object>> getTopOverdueUsers(int limit) {
        return baseMapper.topOverdueUsers(limit);
    }

    @Override
    public Map<String, Object> getBillStatistics(String startDate) {
        LocalDateTime date = LocalDateTime.parse(startDate + "T00:00:00");
        
        Map<String, Object> result = new HashMap<>();
        result.put("byStatus", baseMapper.countByStatus(date));
        result.put("sumAmount", baseMapper.sumAmount(date));
        result.put("collectionRate", baseMapper.calculateCollectionRate(date));
        result.put("byPriceType", baseMapper.sumByPriceType(date));
        
        return result;
    }

    @Override
    public Map<String, Object> getRevenueStatistics(String startDate, String endDate) {
        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end = java.time.LocalDate.parse(endDate).plusDays(1).atStartOfDay();
        if (!start.isBefore(end)) throw new IllegalArgumentException("结束日期不得早于开始日期");
        Map<String,Object> created = baseMapper.createdBetween(start,end);
        return Map.of(
                "totalAmount", created.get("amount"),
                "paidAmount", baseMapper.receiptsBetween(start,end),
                "collectionRate", Map.of("total",created.get("total"),"paid",created.get("paid")),
                "definition", "应收及结清笔数按账单创建日期；登记收入按收款流水日期，含部分付款。起止日期均包含。"
        );
    }

    /**
     * 计算费用（阶梯水价）
     */
    private Map<String, Object> calculateFee(BigDecimal usage, String userType) {
        BigDecimal waterFee;
        List<Map<String, Object>> ladderDetail = new ArrayList<>();

        if ("residential".equals(userType)) {
            // 居民阶梯水价
            BigDecimal remaining = usage;
            waterFee = BigDecimal.ZERO;

            // 第一阶梯
            if (remaining.compareTo(BigDecimal.valueOf(ladder1)) > 0) {
                ladderDetail.add(Map.of("ladder", 1, "usage", ladder1, "price", price1, 
                        "amount", price1.multiply(BigDecimal.valueOf(ladder1))));
                waterFee = waterFee.add(price1.multiply(BigDecimal.valueOf(ladder1)));
                remaining = remaining.subtract(BigDecimal.valueOf(ladder1));

                // 第二阶梯
                if (remaining.compareTo(BigDecimal.valueOf(ladder2 - ladder1)) > 0) {
                    int usage2 = ladder2 - ladder1;
                    ladderDetail.add(Map.of("ladder", 2, "usage", usage2, "price", price2,
                            "amount", price2.multiply(BigDecimal.valueOf(usage2))));
                    waterFee = waterFee.add(price2.multiply(BigDecimal.valueOf(usage2)));
                    remaining = remaining.subtract(BigDecimal.valueOf(usage2));

                    // 第三阶梯
                    ladderDetail.add(Map.of("ladder", 3, "usage", remaining, "price", price3,
                            "amount", price3.multiply(remaining)));
                    waterFee = waterFee.add(price3.multiply(remaining));
                } else {
                    ladderDetail.add(Map.of("ladder", 2, "usage", remaining, "price", price2,
                            "amount", price2.multiply(remaining)));
                    waterFee = waterFee.add(price2.multiply(remaining));
                }
            } else {
                ladderDetail.add(Map.of("ladder", 1, "usage", remaining, "price", price1,
                        "amount", price1.multiply(remaining)));
                waterFee = price1.multiply(remaining);
            }
        } else if ("commercial".equals(userType)) {
            waterFee = commercialPrice.multiply(usage);
            ladderDetail.add(Map.of("ladder", 1, "usage", usage, "price", commercialPrice,
                    "amount", waterFee));
        } else {
            waterFee = industrialPrice.multiply(usage);
            ladderDetail.add(Map.of("ladder", 1, "usage", usage, "price", industrialPrice,
                    "amount", waterFee));
        }

        // 污水处理费
        BigDecimal sewageFee = waterFee.multiply(sewageRate).setScale(2, RoundingMode.HALF_UP);

        return Map.of(
                "waterFee", waterFee.setScale(2, RoundingMode.HALF_UP),
                "sewageFee", sewageFee,
                "ladderDetail", ladderDetail
        );
    }

    private String generateBillNo() {
        return "BILL-" + UUID.randomUUID().toString().replace("-", "");
    }

    private Map<String, Object> generatedResult(Bill bill, boolean existing) {
        return successResult(Map.of("billId", bill.getId(), "billNo", bill.getBillNo(),
                "usage", bill.getUsageAmount(), "totalAmount", bill.getTotalAmount(), "existing", existing));
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("费用明细序列化失败", e);
        }
    }

    private Map<String, Object> paymentResult(Bill bill, BillPayment payment, boolean replayed) {
        BigDecimal paid = bill.getPaidAmount() == null ? BigDecimal.ZERO : bill.getPaidAmount();
        return successResult(Map.of("billId", bill.getId(), "paidAmount", paid,
                "remainingAmount", bill.getTotalAmount().subtract(paid), "status", bill.getStatus(),
                "payment", payment, "replayed", replayed));
    }

    private Map<String, Object> successResult(Object data) {
        return Map.of("success", true, "data", data);
    }

    private Map<String, Object> errorResult(String message) {
        return Map.of("success", false, "message", message);
    }
}
