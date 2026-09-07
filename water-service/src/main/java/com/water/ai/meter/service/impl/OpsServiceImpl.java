package com.water.ai.meter.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.water.ai.meter.mapper.OpsMapper;
import com.water.ai.meter.mapper.SysUserMapper;
import com.water.ai.meter.service.OpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsServiceImpl implements OpsService {

    private final OpsMapper opsMapper;
    private final SysUserMapper sysUserMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${agent-engine.url:http://localhost:8087}")
    private String agentEngineUrl;

    @Override
    public Map<String, Object> cockpit() {
        long meters = opsMapper.countMeters();
        long online = opsMapper.countOnlineMeters();
        BigDecimal todayUsage = nvl(opsMapper.todayUsage());
        BigDecimal todayPaid = nvl(opsMapper.todayPaid());
        BigDecimal monthPaid = nvl(opsMapper.monthPaid());
        long monthBills = opsMapper.monthBillCount();
        long monthPaidBills = opsMapper.monthPaidBillCount();
        long openAnomaly = opsMapper.openAnomalyCount();
        long lowConf = opsMapper.lowConfidencePending();
        long todayAi = opsMapper.todayAiReadingCount();
        Map<String, Object> unpaid = opsMapper.unpaidSummary();

        double collectionRate = monthBills == 0 ? 0 :
                BigDecimal.valueOf(monthPaidBills * 100.0 / monthBills).setScale(1, RoundingMode.HALF_UP).doubleValue();

        java.sql.Date lastRead = opsMapper.lastReadingDate();
        java.sql.Date lastPaid = opsMapper.lastPaidDate();
        String readAsOf = formatMd(lastRead);
        String paidAsOf = formatMd(lastPaid);
        boolean readStale = isBeforeToday(lastRead);
        boolean paidStale = isBeforeToday(lastPaid);

        List<Map<String, Object>> kpis = List.of(
                kpi("usage", "今日用水量", todayUsage.setScale(1, RoundingMode.HALF_UP).toPlainString(), "吨",
                        (readStale ? "数据截止 " + readAsOf + " · " : "") + "在线水表 " + online + "/" + meters, "teal"),
                kpi("revenue", "今日实收", todayPaid.setScale(2, RoundingMode.HALF_UP).toPlainString(), "元",
                        (paidStale ? "数据截止 " + paidAsOf + " · " : "") +
                        "本月实收 " + monthPaid.setScale(0, RoundingMode.HALF_UP) + " · 收费率 " + collectionRate + "%", "gold"),
                kpi("risk", "待处置异常", String.valueOf(openAnomaly), "项",
                        "低置信度待审 " + lowConf, "coral"),
                kpi("ai", "今日AI抄表", String.valueOf(todayAi), "次",
                        (readStale ? "数据截止 " + readAsOf + " · " : "") + "人机协同处理中", "cyan")
        );

        List<Map<String, Object>> signals = buildSignals(lowConf, unpaid);
        String advice = buildAdvice(signals, lowConf, unpaid);

        Map<String, Object> classicStats = new LinkedHashMap<>();
        classicStats.put("totalMeters", meters);
        classicStats.put("onlineMeters", online);
        classicStats.put("todayReading", opsMapper.todayReadingCount());
        classicStats.put("aiReading", todayAi);
        classicStats.put("monthRevenue", monthPaid.setScale(0, RoundingMode.HALF_UP));
        classicStats.put("collectionRate", collectionRate);
        classicStats.put("anomalyCount", openAnomaly);
        classicStats.put("pendingAnomaly", openAnomaly);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpis", kpis);
        result.put("signals", signals);
        result.put("zones", twinZones());
        result.put("advice", advice);
        result.put("unpaid", unpaid);
        result.put("classicStats", classicStats);
        result.put("usageTrend", opsMapper.usageTrend14d());
        result.put("revenueTrend", opsMapper.revenueTrend6m());
        result.put("anomalyDist", opsMapper.anomalyTypeDist());
        result.put("readingTypeDist", opsMapper.readingTypeDist());
        result.put("recentReadings", recentReadings(8));
        result.put("pendingWorkOrders", opsMapper.pendingWorkOrders(8));
        return result;
    }

    private List<Map<String, Object>> buildSignals(long lowConf, Map<String, Object> unpaid) {
        List<Map<String, Object>> signals = new ArrayList<>();
        List<Map<String, Object>> open = opsMapper.openSignals(10);

        for (Map<String, Object> an : open) {
            String severity = str(an.get("severity"));
            String level = "critical".equals(severity) ? "critical" : ("high".equals(severity) ? "warn" : "info");
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", "an-" + an.get("id"));
            s.put("level", level);
            s.put("title", str(an.get("description")));
            s.put("find", "类型 " + an.get("anomaly_type") + " · AI评分 " + an.get("ai_score"));
            s.put("judge", "严重程度 " + severity);
            s.put("action", "优先检查 " + an.get("meter_no"));
            s.put("area", an.get("area_name") == null ? "未知区域" : an.get("area_name"));
            s.put("meterNo", an.get("meter_no"));
            signals.add(s);
        }

        if (lowConf > 0) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", "ocr-low");
            s.put("level", "warn");
            s.put("title", lowConf + " 条AI抄表置信度偏低");
            s.put("find", "OCR置信度 < 70% 且仍待审核");
            s.put("judge", "误读风险偏高，不宜自动入库");
            s.put("action", "进入抄表可信度队列人工审核");
            s.put("area", "全域");
            signals.add(s);
        }

        Object unpaidAmount = unpaid == null ? 0 : unpaid.get("unpaid_amount");
        Object unpaidCount = unpaid == null ? 0 : unpaid.get("unpaid_count");
        if (unpaidCount != null && new BigDecimal(unpaidCount.toString()).compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", "unpaid");
            s.put("level", "info");
            s.put("title", "存在欠费账单需运营干预");
            s.put("find", "欠费账单 " + unpaidCount + " 笔，金额 " + unpaidAmount);
            s.put("judge", "建议差异化催缴，异常高账单先复核");
            s.put("action", "打开智能收费策略并发送提醒");
            s.put("area", "全域");
            signals.add(s);
        }
        return signals;
    }

    private String buildAdvice(List<Map<String, Object>> signals, long lowConf, Map<String, Object> unpaid) {
        StringBuilder sb = new StringBuilder();
        long critical = signals.stream().filter(s -> "critical".equals(s.get("level"))).count();
        if (critical > 0) {
            sb.append("优先处置 ").append(critical).append(" 项紧急异常；");
        }
        if (lowConf > 0) {
            sb.append("对 ").append(lowConf).append(" 条低置信度抄表转入人工协同；");
        }
        if (unpaid != null && unpaid.get("unpaid_count") != null) {
            sb.append("同步跟进欠费账单 ").append(unpaid.get("unpaid_count")).append(" 笔。");
        }
        if (sb.length() == 0) {
            return "当前无高优先级风险，保持巡检节奏即可。";
        }
        return sb.toString();
    }

    @Override
    public List<Map<String, Object>> twinZones() {
        List<Map<String, Object>> zones = opsMapper.zoneOverview();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> z : zones) {
            Long areaId = ((Number) z.get("id")).longValue();
            Map<String, Object> nd = opsMapper.areaNightDayUsage(areaId);
            BigDecimal night = nvl(nd == null ? null : nd.get("night_usage"));
            BigDecimal day = nvl(nd == null ? null : nd.get("day_usage"));
            long anomaly = ((Number) z.getOrDefault("anomaly_count", 0)).longValue();
            double leakRate;
            if (day.compareTo(BigDecimal.ZERO) > 0) {
                leakRate = night.multiply(BigDecimal.valueOf(100))
                        .divide(day.add(night), 1, RoundingMode.HALF_UP).doubleValue();
            } else {
                leakRate = anomaly > 0 ? 12.0 : 3.0;
            }
            String status = anomaly >= 3 || leakRate >= 20 ? "danger" : (anomaly >= 1 || leakRate >= 10 ? "warn" : "ok");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", z.get("area_code"));
            item.put("areaId", areaId);
            item.put("name", z.get("area_name"));
            item.put("status", status);
            item.put("meters", z.get("meters"));
            item.put("usage", nvl(z.get("today_usage")).setScale(1, RoundingMode.HALF_UP));
            item.put("anomaly", anomaly);
            item.put("leakRate", leakRate);
            item.put("insight", buildZoneInsight(status, night, day, anomaly));
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> twinMap() {
        Map<String, double[]> zoneCenters = new LinkedHashMap<>();
        zoneCenters.put("A001", new double[]{120.130000, 30.259000});
        zoneCenters.put("B001", new double[]{120.142000, 30.319000});
        zoneCenters.put("C001", new double[]{120.212000, 30.208000});
        zoneCenters.put("D001", new double[]{119.989000, 30.275000});

        Map<String, String> zoneLetters = new LinkedHashMap<>();
        zoneLetters.put("A001", "A");
        zoneLetters.put("B001", "B");
        zoneLetters.put("C001", "C");
        zoneLetters.put("D001", "D");

        Map<String, String> hangzhouNames = new LinkedHashMap<>();
        hangzhouNames.put("A001", "西湖区(示范A)");
        hangzhouNames.put("B001", "拱墅区(示范B)");
        hangzhouNames.put("C001", "滨江区(示范C)");
        hangzhouNames.put("D001", "余杭区(示范D)");

        List<Map<String, Object>> zones = twinZones();
        for (Map<String, Object> z : zones) {
            String code = String.valueOf(z.get("id"));
            double[] center = zoneCenters.getOrDefault(code, new double[]{120.155070, 30.274150});
            z.put("lng", center[0]);
            z.put("lat", center[1]);
            z.put("letter", zoneLetters.getOrDefault(code, code));
            if (hangzhouNames.containsKey(code)) {
                z.put("name", hangzhouNames.get(code));
            }
            z.put("city", "杭州");
        }

        List<Map<String, Object>> meters = new ArrayList<>();
        for (Map<String, Object> m : opsMapper.mapMeters()) {
            Map<String, Object> item = new LinkedHashMap<>();
            String areaCode = String.valueOf(m.get("area_code"));
            item.put("id", m.get("id"));
            item.put("meterNo", m.get("meter_no"));
            item.put("lng", m.get("longitude"));
            item.put("lat", m.get("latitude"));
            item.put("status", m.get("status"));
            item.put("battery", m.get("battery_level"));
            item.put("signal", m.get("signal_strength"));
            item.put("reading", m.get("current_reading"));
            item.put("address", m.get("install_address"));
            item.put("lastReadingTime", m.get("last_reading_time"));
            item.put("areaCode", areaCode);
            item.put("areaName", hangzhouNames.getOrDefault(areaCode, String.valueOf(m.get("area_name"))));
            item.put("userName", m.get("user_name"));
            long openAnomaly = m.get("open_anomaly") == null ? 0 : ((Number) m.get("open_anomaly")).longValue();
            item.put("openAnomaly", openAnomaly);
            item.put("hasAnomaly", openAnomaly > 0);
            meters.add(item);
        }

        Map<String, Object> city = new LinkedHashMap<>();
        city.put("name", "杭州");
        city.put("lng", 120.155070);
        city.put("lat", 30.274150);
        city.put("zoom", 11);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("zones", zones);
        result.put("meters", meters);
        return result;
    }

    @Override
    public Map<String, Object> zoneDetail(String areaCode) {
        Map<String, Object> area = opsMapper.findAreaByCode(areaCode);
        if (area == null || area.get("id") == null) {
            throw new IllegalArgumentException("分区不存在: " + areaCode);
        }
        Long areaId = ((Number) area.get("id")).longValue();

        Map<String, Object> overview = twinZones().stream()
                .filter(z -> areaCode.equals(String.valueOf(z.get("id"))))
                .findFirst()
                .orElseGet(() -> {
                    Map<String, Object> fallback = new LinkedHashMap<>();
                    fallback.put("id", areaCode);
                    fallback.put("areaId", areaId);
                    fallback.put("name", area.get("area_name"));
                    fallback.put("status", "ok");
                    fallback.put("meters", 0);
                    fallback.put("usage", BigDecimal.ZERO);
                    fallback.put("anomaly", 0);
                    fallback.put("leakRate", 0);
                    fallback.put("insight", "暂无聚合数据");
                    return fallback;
                });

        Map<String, Object> nd = opsMapper.areaNightDayUsage(areaId);
        Map<String, Object> meterStats = opsMapper.meterStatsByArea(areaId);
        List<Map<String, Object>> meters = opsMapper.metersByArea(areaId);
        List<Map<String, Object>> anomalies = opsMapper.anomaliesByArea(areaId, 30);
        List<Map<String, Object>> readings = opsMapper.readingsByArea(areaId, 40);

        Map<String, Integer> healthMap = new HashMap<>();
        for (Map<String, Object> h : meterHealth()) {
            healthMap.put(String.valueOf(h.get("meterNo")), (Integer) h.get("health"));
        }
        for (Map<String, Object> m : meters) {
            String no = String.valueOf(m.get("meter_no"));
            m.put("health", healthMap.getOrDefault(no, 80));
            int status = m.get("status") == null ? 0 : ((Number) m.get("status")).intValue();
            m.put("statusLabel", status == 0 ? "正常" : (status == 1 ? "故障" : "停用"));
        }

        Map<String, Object> nightDay = new LinkedHashMap<>();
        nightDay.put("nightUsage", nvl(nd == null ? null : nd.get("night_usage")));
        nightDay.put("dayUsage", nvl(nd == null ? null : nd.get("day_usage")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("area", area);
        result.put("overview", overview);
        result.put("nightDay", nightDay);
        result.put("meterStats", meterStats == null ? Map.of() : meterStats);
        result.put("meters", meters);
        result.put("anomalies", anomalies);
        result.put("readings", readings);
        return result;
    }

    private String buildZoneInsight(String status, BigDecimal night, BigDecimal day, long anomaly) {
        if ("danger".equals(status)) {
            return "近7天夜间用水 " + night + "，日间 " + day + "，开放异常 " + anomaly + "，疑似管网渗漏/表具故障。";
        }
        if ("warn".equals(status)) {
            return "存在风险信号：开放异常 " + anomaly + "，夜间用水占比偏高，建议巡检。";
        }
        return "运行平稳，暂无明显漏损迹象。";
    }

    @Override
    public List<Map<String, Object>> meterHealth() {
        List<Map<String, Object>> rows = opsMapper.meterHealthRows();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            int battery = m.get("battery_level") == null ? 100 : ((Number) m.get("battery_level")).intValue();
            int signal = m.get("signal_strength") == null ? 100 : ((Number) m.get("signal_strength")).intValue();
            int status = m.get("status") == null ? 0 : ((Number) m.get("status")).intValue();
            int days = m.get("days_since_read") == null ? 0 : ((Number) m.get("days_since_read")).intValue();
            int health = 100;
            List<String> reasons = new ArrayList<>();
            if (status == 1) {
                health -= 35;
                reasons.add("水表状态为故障");
            }
            if (battery < 50) {
                health -= (50 - battery) / 2;
                reasons.add("电量下降至 " + battery + "%");
            }
            if (signal < 60) {
                health -= (60 - signal) / 3;
                reasons.add("信号偏弱 " + signal);
            }
            if (days > 3) {
                health -= Math.min(25, days * 3);
                reasons.add("上传间隔增加，已 " + days + " 天未更新");
            }
            BigDecimal cur = nvl(m.get("current_reading"));
            BigDecimal last = nvl(m.get("last_reading"));
            if (last.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal delta = cur.subtract(last);
                if (delta.compareTo(last.multiply(BigDecimal.valueOf(0.3))) > 0) {
                    health -= 15;
                    reasons.add("数据波动异常（读数跳变偏大）");
                }
            }
            health = Math.max(5, Math.min(99, health));
            int fault30d = Math.max(5, Math.min(95, 100 - health + (status == 1 ? 10 : 0)));
            if (reasons.isEmpty()) {
                reasons.add("运行稳定");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("meterNo", m.get("meter_no"));
            item.put("area", m.get("area_name"));
            item.put("health", health);
            item.put("fault30d", fault30d);
            item.put("reasons", reasons);
            item.put("battery", battery);
            item.put("signal", signal);
            list.add(item);
        }
        list.sort(Comparator.comparingInt(o -> (Integer) o.get("health")));
        return list;
    }

    @Override
    public List<Map<String, Object>> ocrTrust(int limit) {
        List<Map<String, Object>> rows = opsMapper.aiReadingExplain(limit);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            BigDecimal conf = nvl(r.get("confidence"));
            int pct = conf.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
            String focus = "数字盘区域";
            String risk = "低";
            try {
                if (r.get("ai_result") != null) {
                    Map<String, Object> ai = objectMapper.readValue(String.valueOf(r.get("ai_result")),
                            new TypeReference<>() {});
                    if (ai.get("focus") != null) focus = String.valueOf(ai.get("focus"));
                    if (ai.get("risk") != null) risk = String.valueOf(ai.get("risk"));
                }
            } catch (Exception ignored) {}
            String suggest = pct >= 90 ? "自动入库" : (pct >= 70 ? "建议人工复核" : "强制人工审核");
            if (pct < 70) risk = "中";
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("meterNo", r.get("meter_no"));
            item.put("userName", r.get("user_name"));
            item.put("reading", r.get("reading_value"));
            item.put("confidence", pct);
            item.put("focus", focus);
            item.put("risk", risk);
            item.put("suggest", suggest);
            item.put("status", r.get("status"));
            item.put("readingTime", r.get("reading_time"));
            list.add(item);
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> billingInsights() {
        List<Map<String, Object>> rows = opsMapper.currentPeriodBillingInsights();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            BigDecimal current = nvl(r.get("usage_amount"));
            BigDecimal avg = nvl(r.get("avg_usage"));
            if (avg.compareTo(BigDecimal.ZERO) == 0) {
                avg = current;
            }
            double growth = avg.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    current.subtract(avg).multiply(BigDecimal.valueOf(100))
                            .divide(avg, 1, RoundingMode.HALF_UP).doubleValue();
            List<String> hypotheses = new ArrayList<>();
            String suggest;
            if (growth >= 100) {
                hypotheses.add("漏水");
                hypotheses.add("家庭人口变化");
                hypotheses.add("仪表异常");
                suggest = "先发送温馨提醒并预约上门核表，暂缓强制催缴";
            } else if (growth >= 40) {
                hypotheses.add("季节性用水上升");
                hypotheses.add("可能存在未报备用水");
                suggest = "触发阶梯说明短信 + 用量核对工单";
            } else {
                hypotheses.add("正常波动");
                suggest = "无需干预";
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("billId", r.get("id"));
            item.put("user", r.get("user_name"));
            item.put("meterNo", r.get("meter_no"));
            item.put("avg12", avg.setScale(1, RoundingMode.HALF_UP));
            item.put("current", current.setScale(1, RoundingMode.HALF_UP));
            item.put("growth", growth);
            item.put("hypotheses", hypotheses);
            item.put("suggest", suggest);
            item.put("status", r.get("status"));
            item.put("totalAmount", r.get("total_amount"));
            list.add(item);
        }
        return list;
    }

    @Override
    public Map<String, Object> assistant(String question) {
        Map<String, Object> cockpit = cockpit();
        Map<String, Object> unpaid = opsMapper.unpaidSummary();
        List<Map<String, Object>> zones = twinZones();
        List<Map<String, Object>> health = meterHealth();

        List<String> analysis = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        // 优先用 agent-engine，失败则基于库内统计给出可解释结论
        boolean agentOk = false;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("mode", "report");
            body.put("report_type", "ops_qa");
            body.put("period", question);
            body.put("context", Map.of(
                    "question", question,
                    "openAnomaly", opsMapper.openAnomalyCount(),
                    "unpaid", unpaid,
                    "zones", zones
            ));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    agentEngineUrl + "/api/analysis/report",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object data = resp.getBody().get("data");
                Object message = resp.getBody().get("message");
                analysis.add("智能体引擎响应：" + (message == null ? "已完成分析" : message));
                if (data != null) {
                    analysis.add("引擎明细：" + data);
                }
                agentOk = true;
            }
        } catch (Exception e) {
            log.warn("agent-engine unavailable, fallback to DB reasoning: {}", e.getMessage());
        }

        if (!agentOk) {
            if (question != null && (question.contains("收入") || question.contains("水费") || question.contains("欠费"))) {
                analysis.add("欠费账单 " + unpaid.get("unpaid_count") + " 笔，欠费金额 " + unpaid.get("unpaid_amount") + "。");
                analysis.add("今日实收与开放异常、低置信度抄表共同影响账期闭环。");
                actions.add("打开智能收费策略，对突增账单先提醒后催缴");
                actions.add("补齐低置信度抄表审核，避免账单缺口");
            } else if (question != null && (question.contains("漏") || question.contains("夜间") || question.contains("管网"))) {
                zones.stream().filter(z -> "danger".equals(z.get("status"))).findFirst().ifPresentOrElse(z -> {
                    analysis.add(z.get("name") + " 处于异常态势：" + z.get("insight"));
                    analysis.add("夜间用水占比偏高，漏损风险升高。");
                    actions.add("优先巡检该区高风险水表");
                    actions.add("结合异常记录派发工单");
                }, () -> {
                    analysis.add("当前分区未出现最高级漏损态势，但仍有预警区需观察。");
                    actions.add("持续监控夜间流量与异常表");
                });
            } else {
                analysis.add("开放异常 " + opsMapper.openAnomalyCount() + " 项；低置信度待审 "
                        + opsMapper.lowConfidencePending() + " 条。");
                health.stream().limit(3).forEach(h ->
                        analysis.add(h.get("meterNo") + " 健康度 " + h.get("health") + "%，30天故障概率 "
                                + h.get("fault30d") + "%"));
                actions.add("进入 AI 运营驾驶舱按优先级处置");
                actions.add("对健康度最低的水表生成巡检工单");
            }
        } else {
            actions.add("结合驾驶舱信号执行建议动作");
            actions.add("必要时人工复核 agent 结论");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("analysis", analysis);
        result.put("actions", actions);
        result.put("source", agentOk ? "agent-engine+db" : "database");
        result.put("cockpitSnapshot", Map.of(
                "kpis", cockpit.get("kpis"),
                "advice", cockpit.get("advice")
        ));
        return result;
    }

    @Override
    public List<Map<String, Object>> recentReadings(int limit) {
        return opsMapper.recentReadings(limit);
    }

    private Map<String, Object> kpi(String key, String label, String value, String unit, String hint, String tone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("value", value);
        m.put("unit", unit);
        m.put("hint", hint);
        m.put("tone", tone);
        return m;
    }

    private BigDecimal nvl(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String formatMd(java.sql.Date d) {
        return d == null ? "" : d.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
    }

    private boolean isBeforeToday(java.sql.Date d) {
        return d != null && d.toLocalDate().isBefore(java.time.LocalDate.now());
    }
}
