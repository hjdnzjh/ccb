-- 运营决策平台业务种子（真实入库）
USE water_meter_db;

INSERT INTO `area` (`area_code`, `area_name`, `parent_id`, `level`) VALUES
('B001', '示范区B', 0, 3),
('C001', '示范区C', 0, 3),
('D001', '示范区D', 0, 3)
ON DUPLICATE KEY UPDATE area_name=VALUES(area_name);

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `user_type`, `area_id`, `address`, `credit_level`)
SELECT 'admin', 'admin123', '运营官', '13900000000', 'residential', 1, '运营中心', 'A'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='admin' AND deleted=0);

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `user_type`, `area_id`, `address`, `credit_level`)
SELECT 'U004', 'pass123', '赵六', '13800138004', 'residential', (SELECT id FROM area WHERE area_code='B001' LIMIT 1), '示范区B1号', 'B'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='U004');

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `user_type`, `area_id`, `address`, `credit_level`)
SELECT 'U005', 'pass123', '钱七', '13800138005', 'commercial', (SELECT id FROM area WHERE area_code='B001' LIMIT 1), '示范区B2号', 'A'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='U005');

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `user_type`, `area_id`, `address`, `credit_level`)
SELECT 'U006', 'pass123', '孙八', '13800138006', 'residential', (SELECT id FROM area WHERE area_code='C001' LIMIT 1), '示范区C1号', 'A'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='U006');

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `user_type`, `area_id`, `address`, `credit_level`)
SELECT 'U007', 'pass123', '周九', '13800138007', 'industrial', (SELECT id FROM area WHERE area_code='D001' LIMIT 1), '示范区D厂房', 'B'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='U007');

INSERT INTO `water_meter` (`meter_no`, `meter_type`, `comm_type`, `user_id`, `area_id`, `install_address`, `status`, `current_reading`, `last_reading`, `last_reading_time`, `signal_strength`, `battery_level`)
SELECT 'WM-A001-0312', 'digital', 'NB-IoT', 1, 1, '示范区A管网3号', 0, 746.0, 520.0, DATE_SUB(NOW(), INTERVAL 2 DAY), 62, 38
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM water_meter WHERE meter_no='WM-A001-0312');

INSERT INTO `water_meter` (`meter_no`, `meter_type`, `comm_type`, `user_id`, `area_id`, `install_address`, `status`, `current_reading`, `last_reading`, `last_reading_time`, `signal_strength`, `battery_level`)
SELECT 'WM-A001-0315', 'digital', 'NB-IoT', 1, 1, '示范区A管网3号支线', 1, 812.0, 780.0, DATE_SUB(NOW(), INTERVAL 5 DAY), 45, 28
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM water_meter WHERE meter_no='WM-A001-0315');

INSERT INTO `water_meter` (`meter_no`, `meter_type`, `comm_type`, `user_id`, `area_id`, `install_address`, `status`, `current_reading`, `last_reading`, `last_reading_time`, `signal_strength`, `battery_level`)
SELECT 'WM-A001-0320', 'digital', '4G', 2, 1, '示范区A管网3号末梢', 0, 930.0, 700.0, DATE_SUB(NOW(), INTERVAL 1 DAY), 55, 41
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM water_meter WHERE meter_no='WM-A001-0320');

INSERT INTO `water_meter` (`meter_no`, `meter_type`, `comm_type`, `user_id`, `area_id`, `install_address`, `status`, `current_reading`, `last_reading`, `last_reading_time`, `signal_strength`, `battery_level`)
SELECT 'WM-B002-0188', 'digital', 'LoRa', (SELECT id FROM sys_user WHERE username='U004' LIMIT 1), (SELECT id FROM area WHERE area_code='B001' LIMIT 1), '示范区B主干', 0, 420.0, 400.0, DATE_SUB(NOW(), INTERVAL 1 DAY), 78, 66
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM water_meter WHERE meter_no='WM-B002-0188');

INSERT INTO `water_meter` (`meter_no`, `meter_type`, `comm_type`, `user_id`, `area_id`, `install_address`, `status`, `current_reading`, `last_reading`, `last_reading_time`, `signal_strength`, `battery_level`)
SELECT 'WM-C003-0042', 'digital', 'NB-IoT', (SELECT id FROM sys_user WHERE username='U006' LIMIT 1), (SELECT id FROM area WHERE area_code='C001' LIMIT 1), '示范区C小区', 0, 210.0, 195.0, NOW(), 92, 88
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM water_meter WHERE meter_no='WM-C003-0042');

INSERT INTO `water_meter` (`meter_no`, `meter_type`, `comm_type`, `user_id`, `area_id`, `install_address`, `status`, `current_reading`, `last_reading`, `last_reading_time`, `signal_strength`, `battery_level`)
SELECT 'WM-D004-0007', 'wheel', '4G', (SELECT id FROM sys_user WHERE username='U007' LIMIT 1), (SELECT id FROM area WHERE area_code='D001' LIMIT 1), '示范区D厂房', 0, 3500.0, 3200.0, DATE_SUB(NOW(), INTERVAL 3 DAY), 70, 60
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM water_meter WHERE meter_no='WM-D004-0007');

DELETE FROM meter_reading WHERE remark = 'SEED_OPS';
DELETE FROM bill WHERE remark = 'SEED_OPS';
DELETE FROM anomaly_record WHERE remark = 'SEED_OPS';

INSERT INTO meter_reading
(`meter_id`, `meter_no`, `user_id`, `reading_value`, `usage_amount`, `reading_type`, `confidence`, `status`, `reading_time`, `reading_period`, `anomaly_flag`, `anomaly_type`, `remark`, `ai_result`)
SELECT m.id, m.meter_no, m.user_id,
       m.last_reading + d.day_idx * 8 + IF(d.night=1, 18, 4),
       IF(d.night=1, 18, 4) + IF(m.meter_no LIKE 'WM-A001-03%', d.day_idx * 2, 0),
       IF(d.ai=1, 'ai_image', 'remote'),
       d.conf, d.st,
       DATE_SUB(NOW(), INTERVAL d.day_idx DAY) + INTERVAL IF(d.night=1, 2, 14) HOUR,
       DATE_FORMAT(DATE_SUB(NOW(), INTERVAL d.day_idx DAY), '%Y-%m'),
       IF(m.meter_no LIKE 'WM-A001-03%' AND d.night=1, 1, 0),
       IF(m.meter_no LIKE 'WM-A001-03%' AND d.night=1, 'night_surge', NULL),
       'SEED_OPS',
       JSON_OBJECT('focus', '数字盘区域', 'risk', IF(d.conf IS NOT NULL AND d.conf < 0.7, '中', '低'))
FROM water_meter m
JOIN (
  SELECT 1 AS day_idx, 1 AS night, 1 AS ai, 0.97 AS conf, 1 AS st UNION ALL
  SELECT 2, 1, 1, 0.62, 0 UNION ALL
  SELECT 3, 1, 1, 0.88, 0 UNION ALL
  SELECT 4, 0, 0, NULL, 1 UNION ALL
  SELECT 5, 1, 1, 0.91, 1 UNION ALL
  SELECT 6, 1, 1, 0.55, 0 UNION ALL
  SELECT 7, 0, 1, 0.81, 0
) d
WHERE m.deleted = 0;

INSERT INTO bill
(`bill_no`, `user_id`, `meter_id`, `bill_period`, `start_reading`, `end_reading`, `usage_amount`,
 `water_fee`, `sewage_fee`, `total_amount`, `paid_amount`, `price_type`, `status`, `due_date`, `remark`)
SELECT
  CONCAT('BILL-', DATE_FORMAT(NOW(), '%Y%m'), '-', m.id, '-', n.n),
  m.user_id, m.id,
  DATE_FORMAT(DATE_SUB(NOW(), INTERVAL n.n MONTH), '%Y-%m'),
  GREATEST(m.current_reading - IF(n.n=0 AND m.meter_no IN ('WM-A001-0001','WM-A001-0312'), 80, 20), 0),
  m.current_reading,
  IF(n.n=0 AND m.meter_no IN ('WM-A001-0001','WM-A001-0312'), 80, GREATEST(20 - n.n, 8)),
  IF(n.n=0 AND m.meter_no IN ('WM-A001-0001','WM-A001-0312'), 220.00, 41.40),
  IF(n.n=0 AND m.meter_no IN ('WM-A001-0001','WM-A001-0312'), 198.00, 37.26),
  IF(n.n=0 AND m.meter_no IN ('WM-A001-0001','WM-A001-0312'), 418.00, 78.66),
  IF(n.n=0, 0, 78.66),
  'residential',
  IF(n.n=0, 2, 1),
  DATE_SUB(NOW(), INTERVAL IF(n.n=0, 5, -10) DAY),
  'SEED_OPS'
FROM water_meter m
JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2) n
WHERE m.deleted=0 AND m.user_id IS NOT NULL;

INSERT INTO anomaly_record
(`anomaly_no`, `meter_id`, `user_id`, `anomaly_type`, `severity`, `ai_score`, `description`, `detection_detail`, `status`, `detected_time`, `remark`)
SELECT
  CONCAT('AN-', m.id, '-', UNIX_TIMESTAMP()),
  m.id, m.user_id, 'night_usage', 'critical', 0.8600,
  CONCAT('夜间流量异常，疑似漏水：', m.meter_no),
  JSON_OBJECT('night_growth', 0.42, 'meters', m.meter_no),
  0, NOW(), 'SEED_OPS'
FROM water_meter m WHERE m.meter_no IN ('WM-A001-0312','WM-A001-0315','WM-A001-0320');

INSERT INTO anomaly_record
(`anomaly_no`, `meter_id`, `user_id`, `anomaly_type`, `severity`, `ai_score`, `description`, `detection_detail`, `status`, `detected_time`, `remark`)
SELECT
  CONCAT('AN-BAT-', m.id, '-', UNIX_TIMESTAMP()),
  m.id, m.user_id, 'meter_fault', 'high', 0.9200,
  CONCAT('电量偏低 ', m.battery_level, '%'),
  JSON_OBJECT('battery', m.battery_level, 'signal', m.signal_strength),
  0, NOW(), 'SEED_OPS'
FROM water_meter m WHERE m.battery_level < 50 AND m.deleted=0;
