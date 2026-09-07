package com.water.ai.meter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.water.ai.meter.entity.Bill;
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
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate;

    @Value("${agent-engine.url:http://localhost:8087}")
    private String agentEngineUrl;

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
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateBill(Long meterId, Long readingId) {
        log.info("生成账单: meterId={}, readingId={}", meterId, readingId);

        // 获取水表和抄表信息
        WaterMeter meter = waterMeterMapper.selectById(meterId);
        MeterReading reading = meterReadingMapper.selectById(readingId);
        
        if (meter == null || reading == null) {
            return errorResult("水表或抄表记录不存在");
        }

        // 获取用户信息
        SysUser user = sysUserMapper.selectById(meter.getUserId());
        if (user == null) {
            return errorResult("用户不存在");
        }

        // 计算用水量
        BigDecimal usage = reading.getReadingValue().subtract(
                reading.getReadingValue() != null ? reading.getReadingValue() : meter.getLastReading());

        // 计算费用
        Map<String, Object> feeResult = calculateFee(usage, user.getUserType());
        BigDecimal waterFee = (BigDecimal) feeResult.get("waterFee");
        BigDecimal sewageFee = (BigDecimal) feeResult.get("sewageFee");
        BigDecimal totalAmount = waterFee.add(sewageFee);

        // 生成账单编号
        String billNo = generateBillNo();

        // 创建账单
        Bill bill = Bill.builder()
                .billNo(billNo)
                .userId(user.getId())
                .meterId(meterId)
                .readingId(readingId)
                .billPeriod(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .startReading(reading.getReadingValue())
                .endReading(reading.getReadingValue())
                .usageAmount(usage)
                .waterFee(waterFee)
                .sewageFee(sewageFee)
                .totalAmount(totalAmount)
                .priceType(user.getUserType())
                .ladderDetail(feeResult.get("ladderDetail").toString())
                .status(0)
                .dueDate(LocalDateTime.now().plusDays(15))
                .build();

        this.save(bill);

        log.info("账单生成成功: billNo={}, amount={}", billNo, totalAmount);

        return successResult(Map.of(
                "billId", bill.getId(),
                "billNo", billNo,
                "usage", usage,
                "totalAmount", totalAmount
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchGenerateBills(List<Long> readingIds) {
        log.info("批量生成账单: count={}", readingIds.size());

        int successCount = 0;
        List<String> errors = new ArrayList<>();

        for (Long readingId : readingIds) {
            try {
                MeterReading reading = meterReadingMapper.selectById(readingId);
                if (reading != null) {
                    generateBill(reading.getMeterId(), readingId);
                    successCount++;
                }
            } catch (Exception e) {
                errors.add("readingId=" + readingId + ": " + e.getMessage());
            }
        }

        return successResult(Map.of(
                "total", readingIds.size(),
                "success", successCount,
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

        Bill bill = this.getById(billId);
        if (bill == null) {
            return errorResult("账单不存在");
        }

        if (bill.getStatus() == 1) {
            return errorResult("账单已支付");
        }

        // 更新账单状态
        bill.setStatus(1);
        bill.setPaidAmount(amount);
        bill.setPaidTime(LocalDateTime.now());
        bill.setPayMethod(payMethod);
        bill.setTradeNo(tradeNo != null ? tradeNo : generateTradeNo());

        this.updateById(bill);

        log.info("账单支付成功: billId={}", billId);

        return successResult(Map.of(
                "billId", billId,
                "paidAmount", amount,
                "paidTime", bill.getPaidTime()
        ));
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
        LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
        
        Map<String, BigDecimal> sumAmount = baseMapper.sumAmount(start);
        Map<String, Object> collectionRate = baseMapper.calculateCollectionRate(start);
        
        return Map.of(
                "totalAmount", sumAmount == null ? BigDecimal.ZERO : sumAmount.getOrDefault("total", BigDecimal.ZERO),
                "paidAmount", sumAmount == null ? BigDecimal.ZERO : sumAmount.getOrDefault("paid", BigDecimal.ZERO),
                "collectionRate", collectionRate
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
        return "BILL-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) 
                + String.format("%04d", new Random().nextInt(10000));
    }

    private String generateTradeNo() {
        return "TRD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private Map<String, Object> successResult(Object data) {
        return Map.of("success", true, "data", data);
    }

    private Map<String, Object> errorResult(String message) {
        return Map.of("success", false, "message", message);
    }
}