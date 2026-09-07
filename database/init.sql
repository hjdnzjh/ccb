-- =====================================================
-- 基于AI的水表抄表收费管理系统 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS water_meter_db 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

USE water_meter_db;

-- =====================================================
-- 1. 区域表
-- =====================================================
CREATE TABLE IF NOT EXISTS `area` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '区域ID',
    `area_code` VARCHAR(50) NOT NULL COMMENT '区域编码',
    `area_name` VARCHAR(100) NOT NULL COMMENT '区域名称',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父区域ID',
    `level` INT DEFAULT 1 COMMENT '层级: 1-省, 2-市, 3-区, 4-街道',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_area_code` (`area_code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域表';

-- =====================================================
-- 2. 用户表
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名/账号',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(加密)',
    `real_name` VARCHAR(50) COMMENT '真实姓名',
    `phone` VARCHAR(20) COMMENT '手机号',
    `email` VARCHAR(100) COMMENT '邮箱',
    `user_type` VARCHAR(20) DEFAULT 'residential' COMMENT '用户类型: residential-居民, commercial-商业, industrial-工业',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-正常, 1-停用',
    `area_id` BIGINT COMMENT '区域ID',
    `address` VARCHAR(255) COMMENT '详细地址',
    `balance` DECIMAL(12,2) DEFAULT 0.00 COMMENT '账户余额',
    `credit_level` CHAR(1) DEFAULT 'A' COMMENT '信用等级: A/B/C/D',
    `last_login_time` DATETIME COMMENT '最后登录时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    `remark` VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`),
    KEY `idx_user_type` (`user_type`),
    KEY `idx_area_id` (`area_id`),
    KEY `idx_credit_level` (`credit_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =====================================================
-- 3. 水表表
-- =====================================================
CREATE TABLE IF NOT EXISTS `water_meter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '水表ID',
    `meter_no` VARCHAR(50) NOT NULL COMMENT '水表编号',
    `meter_type` VARCHAR(20) DEFAULT 'digital' COMMENT '水表类型: digital-电子式, pointer-指针式, wheel-字轮式',
    `comm_type` VARCHAR(20) COMMENT '通讯方式: NB-IoT, LoRa, 4G, wired-有线',
    `user_id` BIGINT COMMENT '用户ID',
    `area_id` BIGINT COMMENT '区域ID',
    `install_address` VARCHAR(255) COMMENT '安装地址',
    `install_date` DATETIME COMMENT '安装日期',
    `caliber` VARCHAR(20) COMMENT '口径',
    `manufacturer` VARCHAR(100) COMMENT '生产厂家',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-正常, 1-故障, 2-停用, 3-更换',
    `current_reading` DECIMAL(12,2) DEFAULT 0.00 COMMENT '当前读数',
    `last_reading` DECIMAL(12,2) DEFAULT 0.00 COMMENT '上次读数',
    `last_reading_time` DATETIME COMMENT '上次抄表时间',
    `device_imei` VARCHAR(50) COMMENT '设备IMEI',
    `sim_card` VARCHAR(20) COMMENT 'SIM卡号',
    `signal_strength` INT DEFAULT 100 COMMENT '信号强度',
    `battery_level` INT DEFAULT 100 COMMENT '电池电量',
    `longitude` DECIMAL(10,6) COMMENT '经度',
    `latitude` DECIMAL(10,6) COMMENT '纬度',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    `remark` VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_meter_no` (`meter_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_area_id` (`area_id`),
    KEY `idx_status` (`status`),
    KEY `idx_comm_type` (`comm_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='水表表';

-- =====================================================
-- 4. 抄表记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS `meter_reading` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `meter_id` BIGINT NOT NULL COMMENT '水表ID',
    `meter_no` VARCHAR(50) COMMENT '水表编号',
    `user_id` BIGINT COMMENT '用户ID',
    `reading_value` DECIMAL(12,2) NOT NULL COMMENT '读数',
    `usage_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '用水量',
    `reading_type` VARCHAR(20) DEFAULT 'manual' COMMENT '抄表方式: ai_image-AI图像, remote-远程, manual-人工',
    `confidence` DECIMAL(5,4) COMMENT 'AI识别置信度',
    `image_url` VARCHAR(500) COMMENT '水表图片URL',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待审核, 1-已确认, 2-异常',
    `reviewer` VARCHAR(50) COMMENT '审核人',
    `review_time` DATETIME COMMENT '审核时间',
    `reading_time` DATETIME NOT NULL COMMENT '抄表时间',
    `reading_period` VARCHAR(10) COMMENT '抄表周期 yyyy-MM',
    `ai_result` TEXT COMMENT 'AI识别原始结果(JSON)',
    `anomaly_flag` TINYINT DEFAULT 0 COMMENT '异常标记: 0-正常, 1-异常',
    `anomaly_type` VARCHAR(50) COMMENT '异常类型',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_meter_id` (`meter_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_reading_time` (`reading_time`),
    KEY `idx_reading_period` (`reading_period`),
    KEY `idx_reading_type` (`reading_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抄表记录表';

-- =====================================================
-- 5. 账单表
-- =====================================================
CREATE TABLE IF NOT EXISTS `bill` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '账单ID',
    `bill_no` VARCHAR(50) NOT NULL COMMENT '账单编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `meter_id` BIGINT COMMENT '水表ID',
    `reading_id` BIGINT COMMENT '抄表记录ID',
    `bill_period` VARCHAR(10) NOT NULL COMMENT '账期 yyyy-MM',
    `start_reading` DECIMAL(12,2) COMMENT '起始读数',
    `end_reading` DECIMAL(12,2) COMMENT '结束读数',
    `usage_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '用水量',
    `water_fee` DECIMAL(12,2) DEFAULT 0.00 COMMENT '水费',
    `sewage_fee` DECIMAL(12,2) DEFAULT 0.00 COMMENT '污水处理费',
    `penalty` DECIMAL(12,2) DEFAULT 0.00 COMMENT '滞纳金',
    `discount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '优惠金额',
    `total_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '应缴金额',
    `paid_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '实缴金额',
    `price_type` VARCHAR(20) COMMENT '费率类型',
    `ladder_detail` TEXT COMMENT '阶梯明细(JSON)',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-未支付, 1-已支付, 2-已逾期, 3-部分支付',
    `due_date` DATETIME COMMENT '应缴日期',
    `paid_time` DATETIME COMMENT '支付时间',
    `pay_method` VARCHAR(20) COMMENT '支付方式: wechat-微信, alipay-支付宝, bank-银行, cash-现金',
    `trade_no` VARCHAR(100) COMMENT '支付流水号',
    `print_count` INT DEFAULT 0 COMMENT '打印次数',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bill_no` (`bill_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_bill_period` (`bill_period`),
    KEY `idx_status` (`status`),
    KEY `idx_due_date` (`due_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单表';

-- =====================================================
-- 6. 异常记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS `anomaly_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '异常ID',
    `anomaly_no` VARCHAR(50) NOT NULL COMMENT '异常编号',
    `meter_id` BIGINT COMMENT '水表ID',
    `user_id` BIGINT COMMENT '用户ID',
    `anomaly_type` VARCHAR(50) NOT NULL COMMENT '异常类型',
    `severity` VARCHAR(20) DEFAULT 'low' COMMENT '严重程度: critical-紧急, high-高, medium-中, low-低',
    `ai_score` DECIMAL(5,4) COMMENT 'AI评分(0-1)',
    `description` VARCHAR(500) COMMENT '异常描述',
    `detection_detail` TEXT COMMENT '检测详情(JSON)',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-处理中, 2-已处理, 3-已关闭',
    `detected_time` DATETIME COMMENT '检测时间',
    `handled_time` DATETIME COMMENT '处理时间',
    `handler` VARCHAR(50) COMMENT '处理人',
    `handle_result` VARCHAR(500) COMMENT '处理结果',
    `work_order_id` BIGINT COMMENT '工单ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    `remark` VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_anomaly_no` (`anomaly_no`),
    KEY `idx_meter_id` (`meter_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_anomaly_type` (`anomaly_type`),
    KEY `idx_severity` (`severity`),
    KEY `idx_status` (`status`),
    KEY `idx_detected_time` (`detected_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常记录表';

-- =====================================================
-- 7. 工单表
-- =====================================================
CREATE TABLE IF NOT EXISTS `work_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '工单编号',
    `order_type` VARCHAR(20) DEFAULT 'normal' COMMENT '工单类型: emergency-紧急, urgent-加急, normal-普通',
    `anomaly_id` BIGINT COMMENT '异常记录ID',
    `meter_id` BIGINT COMMENT '水表ID',
    `user_id` BIGINT COMMENT '用户ID',
    `area_id` BIGINT COMMENT '区域ID',
    `title` VARCHAR(200) COMMENT '工单标题',
    `description` TEXT COMMENT '工单描述',
    `handler_id` BIGINT COMMENT '处理人ID',
    `handler_name` VARCHAR(50) COMMENT '处理人姓名',
    `priority` INT DEFAULT 3 COMMENT '优先级: 1-紧急, 2-高, 3-普通',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待派单, 1-已派单, 2-处理中, 3-已完成, 4-已关闭',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `dispatch_time` DATETIME COMMENT '派单时间',
    `accept_time` DATETIME COMMENT '接单时间',
    `complete_time` DATETIME COMMENT '完成时间',
    `close_time` DATETIME COMMENT '关闭时间',
    `result` TEXT COMMENT '处理结果',
    `images` TEXT COMMENT '处理图片(JSON数组)',
    `rating` INT COMMENT '评价: 1-5星',
    `feedback` VARCHAR(500) COMMENT '评价内容',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    `remark` VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_anomaly_id` (`anomaly_id`),
    KEY `idx_meter_id` (`meter_id`),
    KEY `idx_handler_id` (`handler_id`),
    KEY `idx_area_id` (`area_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';

-- =====================================================
-- 8. 催缴记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS `collection_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '催缴ID',
    `collection_no` VARCHAR(50) NOT NULL COMMENT '催缴编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `bill_id` BIGINT COMMENT '账单ID',
    `overdue_amount` DECIMAL(12,2) COMMENT '欠费金额',
    `overdue_days` INT COMMENT '逾期天数',
    `strategy` VARCHAR(50) COMMENT '催缴策略',
    `channels` VARCHAR(100) COMMENT '催缴渠道',
    `template` VARCHAR(50) COMMENT '通知模板',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-已发送, 1-已响应, 2-已缴费, 3-无效',
    `sent_time` DATETIME COMMENT '发送时间',
    `response_time` DATETIME COMMENT '响应时间',
    `result` VARCHAR(500) COMMENT '催缴结果',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collection_no` (`collection_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_bill_id` (`bill_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='催缴记录表';

-- =====================================================
-- 9. 用水时序数据表 (对应InfluxDB)
-- =====================================================
CREATE TABLE IF NOT EXISTS `water_usage_stats` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `meter_id` BIGINT NOT NULL COMMENT '水表ID',
    `usage_date` DATE NOT NULL COMMENT '统计日期',
    `hour_usage` DECIMAL(12,2) COMMENT '每小时用量',
    `day_usage` DECIMAL(12,2) COMMENT '日用量',
    `month_usage` DECIMAL(12,2) COMMENT '月用量',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_meter_date` (`meter_id`, `usage_date`),
    KEY `idx_usage_date` (`usage_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用水统计表';

-- =====================================================
-- 10. 系统配置表
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `config_type` VARCHAR(50) COMMENT '配置类型',
    `description` VARCHAR(255) COMMENT '描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- =====================================================
-- 初始化数据
-- =====================================================

-- 插入测试区域
INSERT INTO `area` (`area_code`, `area_name`, `parent_id`, `level`) VALUES
('A001', '示范区A', 0, 3),
('A001-01', '街道1', 1, 4),
('A001-02', '街道2', 1, 4);

-- 插入测试用户
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `user_type`, `area_id`, `address`, `credit_level`) VALUES
('U001', '$2a$10$N.zmdr9k7uOCQb3ZQMKT.eX7mI9v0g6nLXBZH9y0nLXBZH9y0nLXBZH9y', '张三', '13800138001', 'residential', 1, '示范区A街道1号', 'A'),
('U002', '$2a$10$N.zmdr9k7uOCQb3ZQMKT.eX7mI9v0g6nLXBZH9y0nLXBZH9y0nLXBZH9y', '李四', '13800138002', 'commercial', 1, '示范区A街道2号', 'B'),
('U003', '$2a$10$N.zmdr9k7uOCQb3ZQMKT.eX7mI9v0g6nLXBZH9y0nLXBZH9y0nLXBZH9y', '王五', '13800138003', 'industrial', 1, '示范区A街道3号', 'A');

-- 插入测试水表
INSERT INTO `water_meter` (`meter_no`, `meter_type`, `comm_type`, `user_id`, `area_id`, `install_address`, `status`, `current_reading`, `last_reading`) VALUES
('WM-A001-0001', 'digital', 'NB-IoT', 1, 1, '示范区A街道1号', 0, 1230.5, 1200.0),
('WM-A001-0002', 'pointer', 'LoRa', 2, 1, '示范区A街道2号', 0, 5680.0, 5500.0),
('WM-A001-0003', 'wheel', '4G', 3, 1, '示范区A街道3号', 0, 12500.0, 12000.0);

-- 插入系统配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `description`) VALUES
('pricing.residential.ladder1', '180', 'pricing', '居民第一阶梯上限(吨)'),
('pricing.residential.price1', '2.07', 'pricing', '居民第一阶梯价格'),
('pricing.residential.ladder2', '260', 'pricing', '居民第二阶梯上限'),
('pricing.residential.price2', '3.10', 'pricing', '居民第二阶梯价格'),
('pricing.residential.price3', '4.65', 'pricing', '居民第三阶梯价格'),
('pricing.commercial', '4.50', 'pricing', '商业用水价格'),
('pricing.industrial', '5.80', 'pricing', '工业用水价格'),
('sewage.rate', '0.9', 'pricing', '污水处理费率'),
('anomaly.sudden_increase_ratio', '3.0', 'anomaly', '突增倍数阈值'),
('anomaly.zero_usage_days', '30', 'anomaly', '零用量天数阈值');

-- =====================================================
-- 创建视图
-- =====================================================

-- 用户水表视图
CREATE OR REPLACE VIEW v_user_meter AS
SELECT 
    u.id as user_id, u.username, u.real_name, u.phone, u.user_type, u.credit_level,
    m.id as meter_id, m.meter_no, m.meter_type, m.comm_type, m.current_reading,
    m.last_reading, m.last_reading_time, m.status as meter_status
FROM sys_user u
LEFT JOIN water_meter m ON u.id = m.user_id AND m.deleted = 0
WHERE u.deleted = 0;

-- 账单统计视图
CREATE OR REPLACE VIEW v_bill_statistics AS
SELECT 
    bill_period,
    COUNT(*) as bill_count,
    SUM(total_amount) as total_amount,
    SUM(paid_amount) as paid_amount,
    SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as paid_count,
    SUM(CASE WHEN status IN (0, 2) THEN 1 ELSE 0 END) as unpaid_count
FROM bill
WHERE deleted = 0
GROUP BY bill_period;

-- =====================================================
-- 创建存储过程
-- =====================================================

DELIMITER //

-- 计算阶梯水费存储过程
CREATE PROCEDURE sp_calculate_ladder_fee(
    IN p_usage DECIMAL(12,2),
    IN p_user_type VARCHAR(20),
    OUT p_water_fee DECIMAL(12,2),
    OUT p_sewage_fee DECIMAL(12,2),
    OUT p_total_fee DECIMAL(12,2)
)
BEGIN
    DECLARE v_ladder1 INT DEFAULT 180;
    DECLARE v_ladder2 INT DEFAULT 260;
    DECLARE v_price1 DECIMAL(10,2) DEFAULT 2.07;
    DECLARE v_price2 DECIMAL(10,2) DEFAULT 3.10;
    DECLARE v_price3 DECIMAL(10,2) DEFAULT 4.65;
    DECLARE v_commercial_price DECIMAL(10,2) DEFAULT 4.50;
    DECLARE v_industrial_price DECIMAL(10,2) DEFAULT 5.80;
    DECLARE v_sewage_rate DECIMAL(5,2) DEFAULT 0.9;
    DECLARE v_remaining DECIMAL(12,2);
    
    SET p_water_fee = 0;
    SET v_remaining = p_usage;
    
    IF p_user_type = 'residential' THEN
        -- 第一阶梯
        IF v_remaining > v_ladder1 THEN
            SET p_water_fee = p_water_fee + v_ladder1 * v_price1;
            SET v_remaining = v_remaining - v_ladder1;
            
            -- 第二阶梯
            IF v_remaining > (v_ladder2 - v_ladder1) THEN
                SET p_water_fee = p_water_fee + (v_ladder2 - v_ladder1) * v_price2;
                SET v_remaining = v_remaining - (v_ladder2 - v_ladder1);
                -- 第三阶梯
                SET p_water_fee = p_water_fee + v_remaining * v_price3;
            ELSE
                SET p_water_fee = p_water_fee + v_remaining * v_price2;
            END IF;
        ELSE
            SET p_water_fee = v_remaining * v_price1;
        END IF;
    ELSEIF p_user_type = 'commercial' THEN
        SET p_water_fee = p_usage * v_commercial_price;
    ELSE
        SET p_water_fee = p_usage * v_industrial_price;
    END IF;
    
    SET p_sewage_fee = ROUND(p_water_fee * v_sewage_rate, 2);
    SET p_water_fee = ROUND(p_water_fee, 2);
    SET p_total_fee = p_water_fee + p_sewage_fee;
END //

DELIMITER ;

-- =====================================================
-- 结束
-- =====================================================