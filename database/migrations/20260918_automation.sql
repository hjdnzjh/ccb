-- Additive and repeatable. Uses the database selected by the caller.
CREATE TABLE IF NOT EXISTS automation_plan (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, interval_minutes INT NOT NULL,
 adaptive BOOLEAN NOT NULL DEFAULT TRUE, max_attempts INT NOT NULL DEFAULT 3,
 retry_seconds INT NOT NULL DEFAULT 30, scenario VARCHAR(30) NOT NULL DEFAULT 'normal',
 increment_amount DECIMAL(12,2) NOT NULL DEFAULT 1.00,
 next_run_at DATETIME NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS automation_plan_meter (
 plan_id BIGINT NOT NULL, meter_id BIGINT NOT NULL, PRIMARY KEY(plan_id,meter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS automation_run (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, plan_id BIGINT NOT NULL,
 request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_automation_run(plan_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS automation_task (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, run_id BIGINT NOT NULL, meter_id BIGINT NOT NULL,
 packet VARCHAR(500) NOT NULL, scenario VARCHAR(30) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'pending', attempts INT NOT NULL DEFAULT 0,
 max_attempts INT NOT NULL, retry_seconds INT NOT NULL, next_attempt_at DATETIME NOT NULL,
 last_error VARCHAR(500), reading_id BIGINT, completed_at DATETIME,
 UNIQUE KEY uk_automation_task(run_id,meter_id), KEY idx_automation_due(status,next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS automation_receipt (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, meter_id BIGINT NOT NULL, reported_at DATETIME NOT NULL,
 packet VARCHAR(500) NOT NULL, reading_id BIGINT NOT NULL, source VARCHAR(30) NOT NULL DEFAULT 'simulated',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_automation_receipt(meter_id,reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS penalty_policy (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, enabled BOOLEAN NOT NULL DEFAULT FALSE,
 grace_days INT NOT NULL DEFAULT 3, daily_rate DECIMAL(9,6) NOT NULL DEFAULT 0.001000,
 cap_ratio DECIMAL(9,6) NOT NULL DEFAULT 0.100000, effective_from DATE NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO penalty_policy(enabled,effective_from)
 SELECT FALSE,CURRENT_DATE WHERE NOT EXISTS(SELECT 1 FROM penalty_policy);
CREATE TABLE IF NOT EXISTS penalty_ledger (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, bill_id BIGINT NOT NULL, accrual_date DATE NOT NULL,
 policy_id BIGINT NOT NULL, principal_outstanding DECIMAL(12,2) NOT NULL,
 amount DECIMAL(12,2) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_penalty_day(bill_id,accrual_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
