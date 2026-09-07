package com.water.ai.meter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.water.ai.meter.entity.MeterReading;
import com.water.ai.meter.entity.WaterMeter;
import com.water.ai.meter.mapper.MeterReadingMapper;
import com.water.ai.meter.mapper.WaterMeterMapper;
import com.water.ai.meter.service.MeterReadingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 抄表服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeterReadingServiceImpl extends ServiceImpl<MeterReadingMapper, MeterReading>
        implements MeterReadingService {

    private final WaterMeterMapper waterMeterMapper;
    private final RestTemplate restTemplate;

    @Value("${agent-engine.url:http://localhost:8087}")
    private String agentEngineUrl;

    @Value("${agent-engine.timeout:30000}")
    private int agentTimeout;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> aiImageReading(Long meterId, String imageData) {
        log.info("AI图像抄表: meterId={}", meterId);

        // 获取水表信息
        WaterMeter meter = waterMeterMapper.selectById(meterId);
        if (meter == null) {
            return errorResult("水表不存在");
        }

        // 调用智能体引擎
        Map<String, Object> context = new HashMap<>();
        context.put("mode", "image");
        context.put("meter_id", meter.getMeterNo());
        context.put("image_data", imageData);

        Map<String, Object> agentResult = callAgentEngine(context);
        
        if (agentResult == null || !Boolean.TRUE.equals(agentResult.get("success"))) {
            return errorResult("智能体引擎调用失败");
        }

        // 解析结果
        Map<String, Object> data = (Map<String, Object>) agentResult.get("data");
        BigDecimal reading = new BigDecimal(data.get("reading").toString());
        BigDecimal confidence = new BigDecimal(data.get("confidence").toString());

        // 保存抄表记录
        MeterReading record = MeterReading.builder()
                .meterId(meterId)
                .meterNo(meter.getMeterNo())
                .userId(meter.getUserId())
                .readingValue(reading)
                .usageAmount(reading.subtract(meter.getCurrentReading()))
                .readingType("ai_image")
                .confidence(confidence)
                .imageUrl("")  // 实际应上传MinIO
                .status(confidence.compareTo(new BigDecimal("0.95")) >= 0 ? 1 : 0)
                .readingTime(LocalDateTime.now())
                .readingPeriod(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .aiResult(agentResult.toString())
                .build();

        this.save(record);

        // 更新水表读数
        if (record.getStatus() == 1) {
            waterMeterMapper.updateReading(meterId, reading, LocalDateTime.now());
        }

        return successResult(Map.of(
                "readingId", record.getId(),
                "reading", reading,
                "confidence", confidence,
                "status", record.getStatus() == 1 ? "已自动确认" : "待人工审核"
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> remoteReading(Long meterId) {
        log.info("远程抄表: meterId={}", meterId);

        WaterMeter meter = waterMeterMapper.selectById(meterId);
        if (meter == null) {
            return errorResult("水表不存在");
        }

        // 调用智能体引擎
        Map<String, Object> context = new HashMap<>();
        context.put("mode", "remote");
        context.put("meter_id", meter.getMeterNo());
        context.put("signal_strength", meter.getSignalStrength());

        Map<String, Object> agentResult = callAgentEngine(context);
        
        if (agentResult == null || !Boolean.TRUE.equals(agentResult.get("success"))) {
            return errorResult("智能体引擎调用失败");
        }

        Map<String, Object> data = (Map<String, Object>) agentResult.get("data");
        BigDecimal reading = new BigDecimal(data.get("reading").toString());

        // 保存记录
        MeterReading record = MeterReading.builder()
                .meterId(meterId)
                .meterNo(meter.getMeterNo())
                .userId(meter.getUserId())
                .readingValue(reading)
                .usageAmount(reading.subtract(meter.getCurrentReading()))
                .readingType("remote")
                .confidence(new BigDecimal("0.99"))
                .status(1)
                .readingTime(LocalDateTime.now())
                .readingPeriod(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .build();

        this.save(record);
        waterMeterMapper.updateReading(meterId, reading, LocalDateTime.now());

        return successResult(Map.of("readingId", record.getId(), "reading", reading));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchReading(Long areaId, Integer meterCount) {
        log.info("批量抄表: areaId={}, meterCount={}", areaId, meterCount);

        // 调用智能体引擎批量抄表
        Map<String, Object> context = new HashMap<>();
        context.put("mode", "schedule");
        context.put("area_id", areaId != null ? "A" + areaId : "all");
        context.put("meter_count", meterCount);

        Map<String, Object> agentResult = callAgentEngine(context);
        
        if (agentResult == null || !Boolean.TRUE.equals(agentResult.get("success"))) {
            return errorResult("智能体引擎调用失败");
        }

        Map<String, Object> data = (Map<String, Object>) agentResult.get("data");
        return successResult(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> manualReading(Long meterId, BigDecimal reading, String operator) {
        log.info("人工抄表: meterId={}, reading={}, operator={}", meterId, reading, operator);

        WaterMeter meter = waterMeterMapper.selectById(meterId);
        if (meter == null) {
            return errorResult("水表不存在");
        }

        // 保存记录
        MeterReading record = MeterReading.builder()
                .meterId(meterId)
                .meterNo(meter.getMeterNo())
                .userId(meter.getUserId())
                .readingValue(reading)
                .usageAmount(reading.subtract(meter.getCurrentReading()))
                .readingType("manual")
                .confidence(new BigDecimal("1.00"))
                .status(1)
                .readingTime(LocalDateTime.now())
                .readingPeriod(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .remark("人工录入，操作人：" + operator)
                .build();

        this.save(record);
        waterMeterMapper.updateReading(meterId, reading, LocalDateTime.now());

        return successResult(Map.of("readingId", record.getId(), "reading", reading));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewReading(Long readingId, String reviewer, boolean approved, String reason) {
        log.info("审核抄表记录: readingId={}, reviewer={}, approved={}", readingId, reviewer, approved);

        MeterReading record = this.getById(readingId);
        if (record == null) {
            return errorResult("抄表记录不存在");
        }

        record.setReviewer(reviewer);
        record.setReviewTime(LocalDateTime.now());

        if (approved) {
            record.setStatus(1);
            waterMeterMapper.updateReading(record.getMeterId(), record.getReadingValue(), LocalDateTime.now());
        } else {
            record.setStatus(2);
            record.setRemark(reason);
        }

        this.updateById(record);

        return successResult(Map.of("readingId", readingId, "status", approved ? "已确认" : "已拒绝"));
    }

    @Override
    public List<MeterReading> getReadingHistory(Long meterId, int limit) {
        return baseMapper.selectHistoryByMeterId(meterId, limit);
    }

    @Override
    public IPage<MeterReading> getPendingReviewList(Page<MeterReading> page) {
        return baseMapper.selectPendingReview(page);
    }

    @Override
    public Map<String, Object> getReadingStatistics(String startDate) {
        LocalDateTime date = LocalDateTime.parse(startDate + "T00:00:00");
        
        Map<String, Object> result = new HashMap<>();
        result.put("byType", baseMapper.countByReadingType(date));
        result.put("aiRecognition", baseMapper.statisticsAiRecognition(date));
        result.put("avgConfidence", baseMapper.avgConfidence(date));
        result.put("byAnomaly", baseMapper.countByAnomalyType(date));
        
        return result;
    }

    @Override
    public Map<String, Object> callAgentEngine(Map<String, Object> context) {
        try {
            String url = agentEngineUrl + "/api/meter-reading";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(context, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);
            
            return response.getBody();
        } catch (Exception e) {
            log.error("调用智能体引擎失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private Map<String, Object> successResult(Object data) {
        return Map.of("success", true, "data", data);
    }

    private Map<String, Object> errorResult(String message) {
        return Map.of("success", false, "message", message);
    }
}