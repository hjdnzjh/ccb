package com.water.ai.meter.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class ReportService {
    public static final List<String> DEFINITIONS=List.of(
            "时间口径：Asia/Shanghai，起止日期均包含；周一至周日，首尾周期按所选范围截断。",
            "用水量：未删除且审核通过(status=1)的抄表记录 usage_amount，按 reading_time 归属，单位立方米。",
            "收入：bill_payment 实际登记收款 amount，按 paid_time 归属，单位元；包含部分付款，不用账单应收或历史 paid_amount 推算。属于线下/演示收款登记，并非支付机构到账证明。",
            "故障分子：范围内未删除异常的去重水表数，类型 meter_fault、valve_fault、collection_failure、communication_error、device_fault、offline；重复告警只计一次，已处理告警仍计入历史。",
            "故障分母：截至对应统计期末已建档的全部水表，包含软删除、停用和更换水表；无建档时间按历史存量计入。系统缺少删除时间历史，故这是累计建档设备故障率，不是在役设备故障率。物理删除的水表及其孤立异常不纳入。",
            "总故障水表按整个范围重新去重，不能把各期分子直接相加；无设备时故障率不适用。快照保存生成时数据库状态，页面及两个导出复用同一快照。"
    );
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ReportService(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public ReportSnapshot generate(ReportRange range) {
        // DATETIME values are local Shanghai wall time. LocalDateTime avoids JVM-zone Timestamp shifts.
        LocalDateTime from=range.startDate().atStartOfDay();
        LocalDateTime until=range.endDate().plusDays(1).atStartOfDay();
        Map<LocalDate,Daily> daily=new TreeMap<>();
        jdbc.query("SELECT DATE(reading_time) d, COALESCE(SUM(usage_amount),0) total, COUNT(*) n FROM meter_reading WHERE deleted=0 AND status=1 AND reading_time>=? AND reading_time<? GROUP BY DATE(reading_time)",
                rs -> { Daily d=daily.computeIfAbsent(rs.getDate("d").toLocalDate(),key->new Daily()); d.usage=rs.getBigDecimal("total"); d.readings=rs.getLong("n"); },from,until);
        jdbc.query("SELECT DATE(paid_time) d, COALESCE(SUM(amount),0) total, COUNT(*) n FROM bill_payment WHERE paid_time>=? AND paid_time<? GROUP BY DATE(paid_time)",
                rs -> { Daily d=daily.computeIfAbsent(rs.getDate("d").toLocalDate(),key->new Daily()); d.revenue=rs.getBigDecimal("total"); d.payments=rs.getLong("n"); },from,until);
        jdbc.query("SELECT DISTINCT DATE(a.detected_time) d, a.meter_id FROM anomaly_record a JOIN water_meter m ON m.id=a.meter_id WHERE a.deleted=0 AND a.anomaly_type IN ('meter_fault','valve_fault','collection_failure','communication_error','device_fault','offline') AND a.detected_time>=? AND a.detected_time<? AND (m.create_time IS NULL OR m.create_time<=a.detected_time)",
                rs -> { daily.computeIfAbsent(rs.getDate("d").toLocalDate(),key->new Daily()).faults.add(rs.getLong("meter_id")); },from,until);
        // Aggregate dates rather than fetching every device; deleted devices deliberately remain in the base.
        Map<LocalDate,Long> created=new TreeMap<>();
        jdbc.query("SELECT COALESCE(DATE(create_time),'1900-01-01') d, COUNT(*) n FROM water_meter WHERE create_time IS NULL OR create_time<? GROUP BY COALESCE(DATE(create_time),'1900-01-01')",
                rs -> { created.put(rs.getDate("d").toLocalDate(),rs.getLong("n")); },until);
        List<ReportSnapshot.Row> rows=range.buckets().stream().map(b -> new ReportSnapshot.Row(b.startDate(),b.endDate(),metrics(b.startDate(),b.endDate(),daily,created))).toList();
        var snapshot=new ReportSnapshot(UUID.randomUUID().toString(),LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0),"Asia/Shanghai",range,
                metrics(range.startDate(),range.endDate(),daily,created),rows,DEFINITIONS);
        try {
            jdbc.update("INSERT INTO report_snapshot(id,generated_at,start_date,end_date,granularity,snapshot_json) VALUES(?,?,?,?,?,?)",
                    snapshot.id(),snapshot.generatedAt(),range.startDate(),range.endDate(),range.granularity().name(),json.writeValueAsString(snapshot));
        } catch(JsonProcessingException e) { throw new IllegalStateException("报表快照保存失败",e); }
        return snapshot;
    }

    public ReportSnapshot get(String id) {
        if(id==null || !id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) throw new IllegalArgumentException("报表编号无效");
        var values=jdbc.queryForList("SELECT snapshot_json FROM report_snapshot WHERE id=?",String.class,id);
        if(values.isEmpty()) throw new ReportNotFoundException();
        try { return json.readValue(values.get(0),ReportSnapshot.class); }
        catch(JsonProcessingException e) { throw new IllegalStateException("报表快照读取失败",e); }
    }
    private static ReportSnapshot.Metrics metrics(LocalDate start,LocalDate end,Map<LocalDate,Daily> daily,Map<LocalDate,Long> created) {
        BigDecimal usage=BigDecimal.ZERO,revenue=BigDecimal.ZERO;
        long readings=0,payments=0;
        Set<Long> faults=new HashSet<>();
        for(var entry:daily.entrySet()) if(!entry.getKey().isBefore(start) && !entry.getKey().isAfter(end)) {
            Daily d=entry.getValue(); usage=usage.add(d.usage); revenue=revenue.add(d.revenue); readings+=d.readings; payments+=d.payments; faults.addAll(d.faults);
        }
        long base=created.entrySet().stream().filter(e->!e.getKey().isAfter(end)).mapToLong(Map.Entry::getValue).sum();
        BigDecimal rate=base==0 ? null : BigDecimal.valueOf(faults.size()).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(base),2,RoundingMode.HALF_UP);
        return new ReportSnapshot.Metrics(usage.setScale(2,RoundingMode.HALF_UP),revenue.setScale(2,RoundingMode.HALF_UP),faults.size(),base,rate,readings,payments);
    }
    private static class Daily {
        BigDecimal usage=BigDecimal.ZERO,revenue=BigDecimal.ZERO;
        long readings,payments;
        Set<Long> faults=new HashSet<>();
    }
    public static class ReportNotFoundException extends RuntimeException {}
}
