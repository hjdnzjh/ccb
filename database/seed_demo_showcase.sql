-- =====================================================
-- 展示用大规模业务数据（可重复执行）
-- 用法: docker exec -i water-mysql mysql -uroot -p123456 water_meter_db < database/seed_demo_showcase.sql
-- =====================================================
USE water_meter_db;
SET NAMES utf8mb4;

-- 修正分区名称（避免乱码）
UPDATE area SET area_name='示范区A' WHERE area_code='A001';
UPDATE area SET area_name='示范区B' WHERE area_code='B001';
UPDATE area SET area_name='示范区C' WHERE area_code='C001';
UPDATE area SET area_name='示范区D' WHERE area_code='D001';

INSERT INTO area (area_code, area_name, parent_id, level)
SELECT 'B001','示范区B',0,3 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area WHERE area_code='B001');
INSERT INTO area (area_code, area_name, parent_id, level)
SELECT 'C001','示范区C',0,3 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area WHERE area_code='C001');
INSERT INTO area (area_code, area_name, parent_id, level)
SELECT 'D001','示范区D',0,3 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area WHERE area_code='D001');

-- 清理旧展示批次（不影响手工录入）
DELETE FROM collection_record WHERE collection_no LIKE 'DEMO-COL-%';
DELETE FROM work_order WHERE remark = 'DEMO_SHOW';
DELETE FROM anomaly_record WHERE remark IN ('SEED_OPS','DEMO_SHOW');
DELETE FROM bill WHERE remark IN ('SEED_OPS','DEMO_SHOW');
DELETE FROM meter_reading WHERE remark IN ('SEED_OPS','DEMO_SHOW');

-- 批量用户（每区 12 户）
INSERT INTO sys_user (username, password, real_name, phone, user_type, area_id, address, credit_level, balance, status)
SELECT
  CONCAT('DEMO_U_', a.area_code, '_', LPAD(n.n, 2, '0')),
  'pass123',
  ELT(1 + (n.n % 10), '陈晨','林岚','黄海','吴桐','徐悦','何宁','罗阳','高翔','梁静','宋宇'),
  CONCAT('139', LPAD(a.id * 100 + n.n, 8, '0')),
  ELT(1 + (n.n % 3), 'residential','commercial','industrial'),
  a.id,
  CONCAT(a.area_name, n.n, '号楼'),
  ELT(1 + (n.n % 4), 'A','B','C','D'),
  ROUND(50 + n.n * 13.5, 2),
  0
FROM area a
JOIN (
  SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6
  UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12
) n
WHERE a.level = 3
  AND NOT EXISTS (
    SELECT 1 FROM sys_user u
    WHERE u.username = CONCAT('DEMO_U_', a.area_code, '_', LPAD(n.n, 2, '0')) AND u.deleted = 0
  );

-- 每用户一块水表
INSERT INTO water_meter (
  meter_no, meter_type, comm_type, user_id, area_id, install_address, status,
  current_reading, last_reading, last_reading_time, signal_strength, battery_level, remark
)
SELECT
  CONCAT('WM-', a.area_code, '-', LPAD(u.id, 4, '0')),
  ELT(1 + (u.id % 3), 'digital','pointer','wheel'),
  ELT(1 + (u.id % 4), 'NB-IoT','LoRa','4G','wired'),
  u.id,
  u.area_id,
  u.address,
  CASE WHEN u.id % 17 = 0 THEN 1 WHEN u.id % 23 = 0 THEN 2 ELSE 0 END,
  ROUND(200 + (u.id % 50) * 37.6, 1),
  ROUND(180 + (u.id % 50) * 30.2, 1),
  DATE_SUB(NOW(), INTERVAL (u.id % 6) DAY),
  GREATEST(28, 100 - (u.id % 70)),
  GREATEST(18, 100 - (u.id % 85)),
  'DEMO_SHOW'
FROM sys_user u
JOIN area a ON a.id = u.area_id
WHERE u.username LIKE 'DEMO_U_%'
  AND NOT EXISTS (
    SELECT 1 FROM water_meter m WHERE m.user_id = u.id AND m.remark = 'DEMO_SHOW' AND m.deleted = 0
  );

-- 近 14 天抄表（白天+夜间，A区夜间偏高用于漏损展示）
INSERT INTO meter_reading (
  meter_id, meter_no, user_id, reading_value, usage_amount, reading_type, confidence,
  status, reading_time, reading_period, anomaly_flag, anomaly_type, remark, ai_result
)
SELECT
  m.id,
  m.meter_no,
  m.user_id,
  ROUND(m.last_reading + d.day_idx * 6 + IF(d.night = 1, 12, 3) + (m.id % 5), 1),
  ROUND(
    IF(d.night = 1,
       IF(a.area_code = 'A001', 14 + d.day_idx * 1.8, 4 + (m.id % 3)),
       5 + (m.id % 4)
    ), 1
  ),
  IF(d.ai = 1, 'ai_image', IF(d.night = 1, 'remote', 'manual')),
  IF(d.ai = 1, ROUND(0.52 + ((m.id + d.day_idx) % 48) / 100, 4), NULL),
  CASE
    WHEN d.ai = 1 AND ((m.id + d.day_idx) % 48) < 18 THEN 0
    WHEN d.ai = 1 AND ((m.id + d.day_idx) % 7) = 0 THEN 2
    ELSE 1
  END,
  DATE_SUB(CURDATE(), INTERVAL d.day_idx DAY) + INTERVAL IF(d.night = 1, 2, 15) HOUR + INTERVAL (m.id % 40) MINUTE,
  DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL d.day_idx DAY), '%Y-%m'),
  IF(a.area_code = 'A001' AND d.night = 1 AND d.day_idx <= 7, 1, 0),
  IF(a.area_code = 'A001' AND d.night = 1 AND d.day_idx <= 7, 'night_surge', NULL),
  'DEMO_SHOW',
  IF(d.ai = 1,
     JSON_OBJECT(
       'focus', IF((m.id + d.day_idx) % 5 = 0, '数字盘+反光干扰区', '数字盘区域'),
       'risk', IF(((m.id + d.day_idx) % 48) < 18, '中', '低')
     ),
     NULL
  )
FROM water_meter m
JOIN area a ON a.id = m.area_id
JOIN (
  SELECT 0 day_idx, 0 night, 1 ai UNION ALL SELECT 0,1,0 UNION ALL
  SELECT 1,0,1 UNION ALL SELECT 1,1,1 UNION ALL
  SELECT 2,0,0 UNION ALL SELECT 2,1,1 UNION ALL
  SELECT 3,0,1 UNION ALL SELECT 3,1,0 UNION ALL
  SELECT 4,0,1 UNION ALL SELECT 4,1,1 UNION ALL
  SELECT 5,0,0 UNION ALL SELECT 5,1,1 UNION ALL
  SELECT 6,0,1 UNION ALL SELECT 6,1,1 UNION ALL
  SELECT 7,0,1 UNION ALL SELECT 7,1,0 UNION ALL
  SELECT 8,0,1 UNION ALL SELECT 9,1,1 UNION ALL
  SELECT 10,0,0 UNION ALL SELECT 11,1,1 UNION ALL
  SELECT 12,0,1 UNION ALL SELECT 13,1,1
) d
WHERE m.deleted = 0
  AND (m.remark = 'DEMO_SHOW' OR m.meter_no LIKE 'WM-A001-%' OR m.meter_no LIKE 'WM-B%' OR m.meter_no LIKE 'WM-C%' OR m.meter_no LIKE 'WM-D%');

-- 近 3 个账期账单（本月部分欠费/逾期，历史已缴）
INSERT INTO bill (
  bill_no, user_id, meter_id, bill_period, start_reading, end_reading, usage_amount,
  water_fee, sewage_fee, total_amount, paid_amount, price_type, status, due_date, paid_time, pay_method, remark
)
SELECT
  CONCAT('DEMO-BILL-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL p.p MONTH), '%Y%m'), '-', m.id),
  m.user_id,
  m.id,
  DATE_FORMAT(DATE_SUB(NOW(), INTERVAL p.p MONTH), '%Y-%m'),
  GREATEST(m.current_reading - IF(p.p = 0 AND a.area_code = 'A001' AND m.id % 4 = 0, 85, 18 + (m.id % 10)), 0),
  m.current_reading,
  IF(p.p = 0 AND a.area_code = 'A001' AND m.id % 4 = 0, 80 + (m.id % 20), 16 + (m.id % 12)),
  IF(p.p = 0 AND a.area_code = 'A001' AND m.id % 4 = 0, 210 + (m.id % 30), 35 + (m.id % 15)),
  IF(p.p = 0 AND a.area_code = 'A001' AND m.id % 4 = 0, 189 + (m.id % 20), 31 + (m.id % 10)),
  IF(p.p = 0 AND a.area_code = 'A001' AND m.id % 4 = 0, 399 + (m.id % 40), 66 + (m.id % 20)),
  IF(p.p = 0, IF(m.id % 3 = 0, 0, ROUND((66 + (m.id % 20)) * 0.4, 2)), IF(p.p = 0 AND a.area_code='A001' AND m.id%4=0, 0, 66 + (m.id % 20))),
  IFNULL((SELECT user_type FROM sys_user WHERE id = m.user_id), 'residential'),
  CASE
    WHEN p.p > 0 THEN 1
    WHEN m.id % 5 = 0 THEN 2
    WHEN m.id % 3 = 0 THEN 0
    ELSE 1
  END,
  DATE_SUB(NOW(), INTERVAL IF(p.p = 0, 3 + (m.id % 8), -12) DAY),
  IF(p.p > 0 OR m.id % 3 <> 0, DATE_SUB(NOW(), INTERVAL (p.p * 20 + m.id % 5) DAY), NULL),
  IF(p.p > 0 OR m.id % 3 <> 0, ELT(1 + (m.id % 3), 'wechat','alipay','bank'), NULL),
  'DEMO_SHOW'
FROM water_meter m
JOIN area a ON a.id = m.area_id
JOIN (SELECT 0 p UNION ALL SELECT 1 UNION ALL SELECT 2) p
WHERE m.deleted = 0 AND m.user_id IS NOT NULL
  AND (m.remark = 'DEMO_SHOW' OR m.meter_no REGEXP 'WM-(A001|B001|C001|D001|B002|C003|D004)');

-- 修正本月已缴账单的实缴额
UPDATE bill
SET paid_amount = total_amount, paid_time = IFNULL(paid_time, DATE_SUB(NOW(), INTERVAL 2 DAY)), pay_method = IFNULL(pay_method, 'wechat')
WHERE remark = 'DEMO_SHOW' AND status = 1 AND (paid_amount IS NULL OR paid_amount = 0);

-- 异常：漏水 / 电量 / 停传 / 突增
INSERT INTO anomaly_record (
  anomaly_no, meter_id, user_id, anomaly_type, severity, ai_score, description, detection_detail, status, detected_time, remark
)
SELECT
  CONCAT('DEMO-AN-LEAK-', m.id),
  m.id, m.user_id, 'night_usage', 'critical', 0.8600,
  CONCAT('夜间流量异常，疑似漏水：', m.meter_no),
  JSON_OBJECT('night_growth', 0.42, 'window_days', 7),
  0, DATE_SUB(NOW(), INTERVAL (m.id % 20) HOUR), 'DEMO_SHOW'
FROM water_meter m
JOIN area a ON a.id = m.area_id
WHERE a.area_code = 'A001' AND m.deleted = 0
LIMIT 12;

INSERT INTO anomaly_record (
  anomaly_no, meter_id, user_id, anomaly_type, severity, ai_score, description, detection_detail, status, detected_time, remark
)
SELECT
  CONCAT('DEMO-AN-BAT-', m.id),
  m.id, m.user_id, 'meter_fault', IF(m.battery_level < 30, 'high', 'medium'), 0.9100,
  CONCAT('电量偏低 ', m.battery_level, '% / 信号 ', m.signal_strength),
  JSON_OBJECT('battery', m.battery_level, 'signal', m.signal_strength),
  IF(m.id % 4 = 0, 1, 0), DATE_SUB(NOW(), INTERVAL (m.id % 48) HOUR), 'DEMO_SHOW'
FROM water_meter m
WHERE m.deleted = 0 AND m.battery_level < 55
LIMIT 20;

INSERT INTO anomaly_record (
  anomaly_no, meter_id, user_id, anomaly_type, severity, ai_score, description, detection_detail, status, detected_time, remark
)
SELECT
  CONCAT('DEMO-AN-STOP-', m.id),
  m.id, m.user_id, 'zero_usage', 'high', 0.7800,
  CONCAT('疑似停传/零用量：', m.meter_no),
  JSON_OBJECT('days_silent', TIMESTAMPDIFF(DAY, IFNULL(m.last_reading_time, m.create_time), NOW())),
  0, NOW(), 'DEMO_SHOW'
FROM water_meter m
WHERE m.deleted = 0 AND (m.status = 1 OR TIMESTAMPDIFF(DAY, IFNULL(m.last_reading_time, m.create_time), NOW()) >= 4)
LIMIT 10;

INSERT INTO anomaly_record (
  anomaly_no, meter_id, user_id, anomaly_type, severity, ai_score, description, detection_detail, status, detected_time, remark
)
SELECT
  CONCAT('DEMO-AN-SPIKE-', m.id),
  m.id, m.user_id, 'sudden_increase', 'medium', 0.7400,
  CONCAT('用量突增待复核：', m.meter_no),
  JSON_OBJECT('hint', 'billing_insight'),
  0, DATE_SUB(NOW(), INTERVAL 6 HOUR), 'DEMO_SHOW'
FROM water_meter m
JOIN area a ON a.id = m.area_id
WHERE a.area_code IN ('A001','B001') AND m.id % 4 = 0 AND m.deleted = 0
LIMIT 8;

-- 工单（关联部分异常）
INSERT INTO work_order (
  order_no, order_type, anomaly_id, meter_id, user_id, area_id, title, description,
  handler_name, priority, status, create_time, remark
)
SELECT
  CONCAT('DEMO-WO-', an.id),
  IF(an.severity = 'critical', 'emergency', 'normal'),
  an.id, an.meter_id, an.user_id,
  (SELECT area_id FROM water_meter WHERE id = an.meter_id),
  CONCAT('处置-', an.anomaly_type),
  an.description,
  IF(an.id % 2 = 0, '巡检一组', '巡检二组'),
  IF(an.severity = 'critical', 1, IF(an.severity = 'high', 2, 3)),
  IF(an.status = 1, 2, 0),
  an.detected_time,
  'DEMO_SHOW'
FROM anomaly_record an
WHERE an.remark = 'DEMO_SHOW' AND an.severity IN ('critical','high')
LIMIT 18;

-- 催缴记录
INSERT INTO collection_record (
  collection_no, user_id, bill_id, overdue_amount, overdue_days, strategy,
  channels, template, status, sent_time, result
)
SELECT
  CONCAT('DEMO-COL-', b.id),
  b.user_id, b.id,
  ROUND(b.total_amount - IFNULL(b.paid_amount, 0), 2),
  GREATEST(1, TIMESTAMPDIFF(DAY, b.due_date, NOW())),
  IF(TIMESTAMPDIFF(DAY, b.due_date, NOW()) > 15, 'intensive', IF(TIMESTAMPDIFF(DAY, b.due_date, NOW()) > 7, 'formal', 'gentle')),
  ELT(1 + (b.id % 3), 'sms','wechat','phone'),
  'usage_reminder',
  IF(b.id % 3 = 0, 1, 0),
  DATE_SUB(NOW(), INTERVAL (b.id % 10) HOUR),
  CONCAT('温馨提醒：待缴账单 ', b.bill_no)
FROM bill b
WHERE b.remark = 'DEMO_SHOW' AND b.status IN (0, 2)
LIMIT 25;

-- 今日额外抄表，抬升驾驶舱「今日」指标
INSERT INTO meter_reading (
  meter_id, meter_no, user_id, reading_value, usage_amount, reading_type, confidence,
  status, reading_time, reading_period, anomaly_flag, remark, ai_result
)
SELECT
  m.id, m.meter_no, m.user_id,
  ROUND(m.current_reading + 2.5 + (m.id % 3), 1),
  ROUND(3.5 + (m.id % 5) * 0.7, 1),
  IF(m.id % 2 = 0, 'ai_image', 'remote'),
  IF(m.id % 2 = 0, ROUND(0.70 + (m.id % 25) / 100, 4), NULL),
  1,
  NOW() - INTERVAL (m.id % 90) MINUTE,
  DATE_FORMAT(NOW(), '%Y-%m'),
  0,
  'DEMO_SHOW',
  IF(m.id % 2 = 0, JSON_OBJECT('focus','数字盘区域','risk','低'), NULL)
FROM water_meter m
WHERE m.deleted = 0
ORDER BY m.id
LIMIT 40;

-- 今日实收：把部分本月已支付账单的 paid_time 调到今天，便于驾驶舱展示
UPDATE bill b
JOIN (
  SELECT id FROM bill WHERE remark = 'DEMO_SHOW' AND status = 1 ORDER BY id LIMIT 18
) t ON b.id = t.id
SET b.paid_time = NOW() - INTERVAL (b.id % 120) MINUTE,
    b.pay_method = IFNULL(b.pay_method, 'wechat');
