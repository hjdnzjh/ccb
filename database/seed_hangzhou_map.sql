-- 杭州示范分区经纬度（可重复执行）
USE water_meter_db;
SET NAMES utf8mb4;

UPDATE area SET area_name='西湖区(示范A)' WHERE area_code='A001';
UPDATE area SET area_name='拱墅区(示范B)' WHERE area_code='B001';
UPDATE area SET area_name='滨江区(示范C)' WHERE area_code='C001';
UPDATE area SET area_name='余杭区(示范D)' WHERE area_code='D001';

-- 按分区在杭州真实片区附近撒点
UPDATE water_meter m
JOIN area a ON a.id = m.area_id AND a.deleted = 0
SET
  m.longitude = CASE a.area_code
    WHEN 'A001' THEN ROUND(120.130000 + ((m.id % 19) - 9) * 0.0038 + ((m.id % 7) - 3) * 0.0007, 6)
    WHEN 'B001' THEN ROUND(120.142000 + ((m.id % 19) - 9) * 0.0036 + ((m.id % 7) - 3) * 0.0006, 6)
    WHEN 'C001' THEN ROUND(120.212000 + ((m.id % 19) - 9) * 0.0035 + ((m.id % 7) - 3) * 0.0006, 6)
    WHEN 'D001' THEN ROUND(119.989000 + ((m.id % 19) - 9) * 0.0040 + ((m.id % 7) - 3) * 0.0007, 6)
    ELSE ROUND(120.155070 + ((m.id % 11) - 5) * 0.002, 6)
  END,
  m.latitude = CASE a.area_code
    WHEN 'A001' THEN ROUND(30.259000 + ((m.id % 17) - 8) * 0.0028 + ((m.id % 5) - 2) * 0.0005, 6)
    WHEN 'B001' THEN ROUND(30.319000 + ((m.id % 17) - 8) * 0.0026 + ((m.id % 5) - 2) * 0.0005, 6)
    WHEN 'C001' THEN ROUND(30.208000 + ((m.id % 17) - 8) * 0.0025 + ((m.id % 5) - 2) * 0.0004, 6)
    WHEN 'D001' THEN ROUND(30.275000 + ((m.id % 17) - 8) * 0.0030 + ((m.id % 5) - 2) * 0.0005, 6)
    ELSE ROUND(30.274150 + ((m.id % 11) - 5) * 0.0015, 6)
  END,
  m.install_address = CASE a.area_code
    WHEN 'A001' THEN CONCAT('杭州市西湖区 ', m.meter_no)
    WHEN 'B001' THEN CONCAT('杭州市拱墅区 ', m.meter_no)
    WHEN 'C001' THEN CONCAT('杭州市滨江区 ', m.meter_no)
    WHEN 'D001' THEN CONCAT('杭州市余杭区 ', m.meter_no)
    ELSE CONCAT('杭州市 ', m.meter_no)
  END;
