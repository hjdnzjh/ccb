package com.water.ai.meter.assistant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class AssistantRepository {
    private final JdbcTemplate jdbc;
    public AssistantRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> areas() {
        return jdbc.queryForList("SELECT id,area_code,area_name,parent_id FROM area WHERE deleted=0 ORDER BY id");
    }

    private String filter(List<Long> areaIds, String meterNo, List<Object> args) {
        String result = "";
        if (!areaIds.isEmpty()) {
            result += " AND m.area_id IN (" + String.join(",", Collections.nCopies(areaIds.size(), "?")) + ")";
            args.addAll(areaIds);
        }
        if (meterNo != null) { result += " AND m.meter_no=?"; args.add(meterNo); }
        return result;
    }

    public List<Map<String,Object>> meters(List<Long> areaIds, String meterNo) {
        List<Object> args = new ArrayList<>();
        // Aggregate anomalies before joining: one row per meter, no multiplied counts.
        String query = """
            SELECT m.id,m.meter_no,m.status,m.battery_level,m.signal_strength,m.last_reading_time,
                   a.area_name,COALESCE(n.open_count,0) open_count,COALESCE(n.severe_count,0) severe_count
            FROM water_meter m LEFT JOIN area a ON a.id=m.area_id AND a.deleted=0
            LEFT JOIN (SELECT meter_id,COUNT(*) open_count,
                       SUM(CASE WHEN severity IN ('critical','high') THEN 1 ELSE 0 END) severe_count
                       FROM anomaly_record WHERE deleted=0 AND status IN (0,1) GROUP BY meter_id) n ON n.meter_id=m.id
            WHERE m.deleted=0 AND m.status IN (0,1)
            """ + filter(areaIds,meterNo,args) + " ORDER BY m.meter_no";
        return jdbc.queryForList(query,args.toArray());
    }

    public List<Map<String,Object>> leakEvidence(List<Long> areaIds, String meterNo) {
        List<Object> args = new ArrayList<>();
        return jdbc.queryForList("""
            SELECT n.anomaly_no,n.anomaly_type,n.severity,n.detected_time,n.description,
                   m.id,m.meter_no,a.area_name
            FROM anomaly_record n JOIN water_meter m ON m.id=n.meter_id
            LEFT JOIN area a ON a.id=m.area_id AND a.deleted=0
            WHERE n.deleted=0 AND n.status IN (0,1) AND m.deleted=0 AND m.status IN (0,1)
              AND n.anomaly_type IN ('leak','leakage','night_usage','sudden_increase','high_flow')
            """ + filter(areaIds,meterNo,args) +
            " ORDER BY FIELD(n.severity,'critical','high','medium','low'),n.detected_time DESC,n.id DESC",args.toArray());
    }

    public Map<String,Object> receipts(LocalDateTime from, LocalDateTime until) {
        return jdbc.queryForMap("SELECT COUNT(*) count,COALESCE(SUM(amount),0) amount FROM bill_payment WHERE paid_time>=? AND paid_time<?",from,until);
    }

    public Map<String,Object> unpaid(LocalDateTime now) {
        return jdbc.queryForMap("""
            SELECT COUNT(*) count,COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) amount,
                   COALESCE(SUM(CASE WHEN due_date<? THEN 1 ELSE 0 END),0) overdue
            FROM bill WHERE deleted=0 AND total_amount>COALESCE(paid_amount,0)
            """,now);
    }
}
