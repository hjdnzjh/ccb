package com.water.ai.meter.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OpsMapper {

    @Select("SELECT COUNT(*) FROM water_meter WHERE deleted = 0")
    long countMeters();

    @Select("SELECT COUNT(*) FROM water_meter WHERE deleted = 0 AND status = 0")
    long countOnlineMeters();

    @Select("""
SELECT MAX(DATE(reading_time)) FROM meter_reading WHERE deleted=0 AND status=1 AND reading_time<=NOW()
        """)
    java.sql.Date lastReadingDate();

    @Select("""
SELECT MAX(DATE(paid_time)) FROM bill_payment WHERE paid_time<=NOW()
        """)
    java.sql.Date lastPaidDate();

    @Select("""
SELECT COALESCE(SUM(usage_amount),0) FROM meter_reading WHERE deleted=0 AND status=1 AND reading_time>=CURDATE() AND reading_time<=NOW()
        """)
    java.math.BigDecimal todayUsage();

    @Select("""
SELECT COALESCE(SUM(amount),0) FROM bill_payment WHERE paid_time>=CURDATE() AND paid_time<=NOW()
        """)
    java.math.BigDecimal todayPaid();

    @Select("""
SELECT COALESCE(SUM(amount),0) FROM bill_payment WHERE paid_time>=DATE_FORMAT(NOW(),'%Y-%m-01') AND paid_time<=NOW()
        """)
    java.math.BigDecimal monthPaid();

    @Select("SELECT COUNT(*) FROM bill WHERE deleted = 0 AND create_time >= DATE_FORMAT(NOW(), '%Y-%m-01')")
    long monthBillCount();

    @Select("SELECT COUNT(*) FROM bill WHERE deleted = 0 AND status = 1 AND create_time >= DATE_FORMAT(NOW(), '%Y-%m-01')")
    long monthPaidBillCount();

    @Select("SELECT COUNT(*) FROM anomaly_record WHERE deleted = 0 AND status IN (0,1)")
    long openAnomalyCount();

    @Select("SELECT COUNT(*) FROM meter_reading WHERE deleted = 0 AND reading_type = 'ai_image' AND confidence < 0.70 AND status = 0")
    long lowConfidencePending();

    @Select("""
SELECT COUNT(*) FROM meter_reading WHERE deleted=0 AND reading_type='ai_image' AND reading_time>=CURDATE() AND reading_time<=NOW()
        """)
    long todayAiReadingCount();

    @Select("""
SELECT a.id,a.area_code,a.area_name,COUNT(m.id) meters,
 COALESCE(SUM(r.usage_amount),0) today_usage,COALESCE(SUM(n.anomaly_count),0) anomaly_count,AVG(m.battery_level) avg_battery
 FROM area a LEFT JOIN water_meter m ON m.area_id=a.id AND m.deleted=0
 LEFT JOIN (SELECT meter_id,SUM(usage_amount) usage_amount FROM meter_reading
 WHERE deleted=0 AND status=1 AND reading_time>=CURDATE() AND reading_time<=NOW() GROUP BY meter_id) r ON r.meter_id=m.id
 LEFT JOIN (SELECT meter_id,COUNT(*) anomaly_count FROM anomaly_record WHERE deleted=0 AND status IN (0,1) GROUP BY meter_id) n ON n.meter_id=m.id
 WHERE a.deleted=0 AND a.level=3 GROUP BY a.id,a.area_code,a.area_name ORDER BY a.id
        """)
    List<Map<String, Object>> zoneOverview();

    @Select("SELECT m.id, m.meter_no, m.area_id, a.area_name, m.status, m.battery_level, m.signal_strength, " +
            "m.last_reading_time, m.current_reading, m.last_reading, " +
            "TIMESTAMPDIFF(DAY, IFNULL(m.last_reading_time, m.create_time), NOW()) AS days_since_read " +
            "FROM water_meter m LEFT JOIN area a ON a.id = m.area_id " +
            "WHERE m.deleted = 0 ORDER BY m.battery_level ASC, m.signal_strength ASC")
    List<Map<String, Object>> meterHealthRows();

    @Select("SELECT r.id, r.meter_no, r.user_id, u.real_name AS user_name, r.reading_value, r.usage_amount, " +
            "r.reading_type, r.confidence, r.status, r.reading_time, r.ai_result, r.anomaly_flag, r.anomaly_type " +
            "FROM meter_reading r LEFT JOIN sys_user u ON u.id = r.user_id " +
            "WHERE r.deleted = 0 ORDER BY r.reading_time DESC LIMIT #{limit}")
    List<Map<String, Object>> recentReadings(int limit);

    @Select("SELECT r.id, r.meter_no, u.real_name AS user_name, r.reading_value, r.confidence, r.status, " +
            "r.reading_time, r.ai_result " +
            "FROM meter_reading r LEFT JOIN sys_user u ON u.id = r.user_id " +
            "WHERE r.deleted = 0 AND r.reading_type = 'ai_image' " +
            "ORDER BY r.confidence ASC, r.reading_time DESC LIMIT #{limit}")
    List<Map<String, Object>> aiReadingExplain(int limit);

    @Select("SELECT b.id, b.bill_no, b.user_id, u.real_name AS user_name, m.meter_no, b.bill_period, " +
            "b.usage_amount, b.total_amount, b.paid_amount, b.status, b.due_date, " +
            "(SELECT AVG(b2.usage_amount) FROM bill b2 WHERE b2.user_id = b.user_id AND b2.deleted = 0 " +
            " AND b2.id <> b.id AND b2.usage_amount IS NOT NULL) AS avg_usage " +
            "FROM bill b " +
            "JOIN sys_user u ON u.id = b.user_id " +
            "LEFT JOIN water_meter m ON m.id = b.meter_id " +
            "WHERE b.deleted = 0 AND b.bill_period = DATE_FORMAT(NOW(), '%Y-%m') " +
            "ORDER BY b.usage_amount DESC")
    List<Map<String, Object>> currentPeriodBillingInsights();

    @Select("SELECT an.id, an.anomaly_no, an.meter_id, m.meter_no, a.area_name, an.anomaly_type, an.severity, " +
            "an.ai_score, an.description, an.status, an.detected_time " +
            "FROM anomaly_record an " +
            "LEFT JOIN water_meter m ON m.id = an.meter_id " +
            "LEFT JOIN area a ON a.id = m.area_id " +
            "WHERE an.deleted = 0 AND an.status IN (0,1) " +
            "ORDER BY FIELD(an.severity,'critical','high','medium','low'), an.detected_time DESC LIMIT #{limit}")
    List<Map<String, Object>> openSignals(int limit);

    @Select("""
SELECT COALESCE(SUM(CASE WHEN HOUR(reading_time) BETWEEN 0 AND 5 THEN usage_amount ELSE 0 END),0) night_usage,
 COALESCE(SUM(CASE WHEN HOUR(reading_time) BETWEEN 6 AND 23 THEN usage_amount ELSE 0 END),0) day_usage
 FROM meter_reading WHERE deleted=0 AND status=1 AND reading_time>=DATE_SUB(NOW(),INTERVAL 7 DAY) AND reading_time<=NOW()
 AND meter_id IN (SELECT id FROM water_meter WHERE area_id=#{areaId} AND deleted=0)
        """)
    Map<String, Object> areaNightDayUsage(Long areaId);

    @Select("""
SELECT COUNT(*) unpaid_count,COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) unpaid_amount FROM bill WHERE deleted=0 AND total_amount>COALESCE(paid_amount,0)
        """)
    Map<String, Object> unpaidSummary();

    @Select("SELECT id, area_code, area_name, parent_id, level FROM area WHERE deleted = 0 AND area_code = #{areaCode} LIMIT 1")
    Map<String, Object> findAreaByCode(String areaCode);

    @Select("SELECT m.id, m.meter_no, m.meter_type, m.comm_type, m.status, m.current_reading, m.last_reading, " +
            "m.last_reading_time, m.signal_strength, m.battery_level, m.install_address, " +
            "u.real_name AS user_name, u.phone AS user_phone " +
            "FROM water_meter m LEFT JOIN sys_user u ON u.id = m.user_id " +
            "WHERE m.deleted = 0 AND m.area_id = #{areaId} ORDER BY m.battery_level ASC, m.status DESC")
    List<Map<String, Object>> metersByArea(Long areaId);

    @Select("SELECT an.id, an.anomaly_no, an.meter_id, m.meter_no, an.anomaly_type, an.severity, " +
            "an.ai_score, an.description, an.status, an.detected_time " +
            "FROM anomaly_record an LEFT JOIN water_meter m ON m.id = an.meter_id " +
            "WHERE an.deleted = 0 AND m.area_id = #{areaId} " +
            "ORDER BY FIELD(an.severity,'critical','high','medium','low'), an.detected_time DESC LIMIT #{limit}")
    List<Map<String, Object>> anomaliesByArea(@org.apache.ibatis.annotations.Param("areaId") Long areaId,
                                              @org.apache.ibatis.annotations.Param("limit") int limit);

    @Select("SELECT r.id, r.meter_no, r.reading_value, r.usage_amount, r.reading_type, r.confidence, " +
            "r.status, r.reading_time, r.anomaly_flag, r.anomaly_type " +
            "FROM meter_reading r JOIN water_meter m ON m.id = r.meter_id " +
            "WHERE r.deleted = 0 AND m.area_id = #{areaId} " +
            "ORDER BY r.reading_time DESC LIMIT #{limit}")
    List<Map<String, Object>> readingsByArea(@org.apache.ibatis.annotations.Param("areaId") Long areaId,
                                             @org.apache.ibatis.annotations.Param("limit") int limit);

    @Select("SELECT COUNT(*) AS meter_count, " +
            "SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS normal_count, " +
            "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS fault_count, " +
            "AVG(battery_level) AS avg_battery, AVG(signal_strength) AS avg_signal " +
            "FROM water_meter WHERE deleted = 0 AND area_id = #{areaId}")
    Map<String, Object> meterStatsByArea(Long areaId);

    @Select("""
SELECT COUNT(*) FROM meter_reading WHERE deleted=0 AND reading_time>=CURDATE() AND reading_time<=NOW()
        """)
    long todayReadingCount();

    @Select("""
WITH RECURSIVE dates AS (SELECT DATE_SUB(CURDATE(),INTERVAL 13 DAY) dt UNION ALL SELECT DATE_ADD(dt,INTERVAL 1 DAY) FROM dates WHERE dt<CURDATE())
 SELECT DATE_FORMAT(d.dt,'%m-%d') label,COALESCE(SUM(r.usage_amount),0) usage_amount FROM dates d
 LEFT JOIN meter_reading r ON r.deleted=0 AND r.status=1 AND r.reading_time>=d.dt AND r.reading_time<DATE_ADD(d.dt,INTERVAL 1 DAY) AND r.reading_time<=NOW()
 GROUP BY d.dt ORDER BY d.dt
        """)
    List<Map<String, Object>> usageTrend14d();

    @Select("""
WITH RECURSIVE months AS (SELECT DATE_SUB(CAST(DATE_FORMAT(CURDATE(),'%Y-%m-01') AS DATE),INTERVAL 5 MONTH) dt UNION ALL SELECT DATE_ADD(dt,INTERVAL 1 MONTH) FROM months WHERE dt<CAST(DATE_FORMAT(CURDATE(),'%Y-%m-01') AS DATE))
 SELECT DATE_FORMAT(m.dt,'%Y-%m') label,
 (SELECT COALESCE(SUM(total_amount),0) FROM bill WHERE deleted=0 AND create_time>=m.dt AND create_time<DATE_ADD(m.dt,INTERVAL 1 MONTH) AND create_time<=NOW()) receivable,
 (SELECT COALESCE(SUM(amount),0) FROM bill_payment WHERE paid_time>=m.dt AND paid_time<DATE_ADD(m.dt,INTERVAL 1 MONTH) AND paid_time<=NOW()) received,
 (SELECT COALESCE(SUM(GREATEST(total_amount-COALESCE(paid_amount,0),0)),0) FROM bill WHERE deleted=0 AND create_time>=m.dt AND create_time<DATE_ADD(m.dt,INTERVAL 1 MONTH) AND create_time<=NOW()) unpaid
 FROM months m ORDER BY m.dt
        """)
    List<Map<String, Object>> revenueTrend6m();

    @Select("SELECT anomaly_type AS name, COUNT(*) AS value FROM anomaly_record " +
            "WHERE deleted = 0 GROUP BY anomaly_type ORDER BY value DESC")
    List<Map<String, Object>> anomalyTypeDist();

    @Select("SELECT reading_type AS name, COUNT(*) AS value FROM meter_reading " +
            "WHERE deleted = 0 GROUP BY reading_type ORDER BY value DESC")
    List<Map<String, Object>> readingTypeDist();

    @Select("SELECT id, order_no, order_type, title, description, priority, status, create_time " +
            "FROM work_order WHERE deleted = 0 AND status IN (0,1,2) " +
            "ORDER BY priority ASC, create_time DESC LIMIT #{limit}")
    List<Map<String, Object>> pendingWorkOrders(int limit);

    @Select("SELECT m.id, m.meter_no, m.longitude, m.latitude, m.status, m.battery_level, m.signal_strength, " +
            "m.current_reading, m.install_address, m.last_reading_time, " +
            "a.area_code, a.area_name, u.real_name AS user_name, " +
            "(SELECT COUNT(*) FROM anomaly_record an WHERE an.meter_id = m.id AND an.deleted = 0 AND an.status IN (0,1)) AS open_anomaly " +
            "FROM water_meter m " +
            "JOIN area a ON a.id = m.area_id AND a.deleted = 0 " +
            "LEFT JOIN sys_user u ON u.id = m.user_id " +
            "WHERE m.deleted = 0 AND m.longitude IS NOT NULL AND m.latitude IS NOT NULL " +
            "ORDER BY a.area_code, m.id")
    List<Map<String, Object>> mapMeters();
}
