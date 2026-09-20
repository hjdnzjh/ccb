-- Additive diagnostic observations: no changes to financial history.
CREATE TABLE IF NOT EXISTS diagnosis_policy (
 id INT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 1, mode VARCHAR(20) NOT NULL DEFAULT 'off',
 meter_ids TEXT NOT NULL, updated_by BIGINT, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO diagnosis_policy(id,meter_ids) VALUES(1,'[]');
CREATE TABLE IF NOT EXISTS meter_observation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,meter_id BIGINT NOT NULL,source VARCHAR(20) NOT NULL DEFAULT 'simulated',
 reported_at DATETIME NOT NULL,received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 flow DECIMAL(15,3) NOT NULL,total DECIMAL(15,2) NOT NULL,temperature DECIMAL(7,2) NOT NULL,
 valve VARCHAR(10) NOT NULL,alarm VARCHAR(20) NOT NULL,packet_hash CHAR(64) NOT NULL,packet VARCHAR(500) NOT NULL,
 quality_status VARCHAR(20) NOT NULL DEFAULT 'valid',
 UNIQUE KEY uk_observation(meter_id,source,reported_at),KEY idx_observation_time(meter_id,reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS observation_attempt (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 meter_id BIGINT,observation_id BIGINT,purpose VARCHAR(20) NOT NULL,packet_hash CHAR(64) NOT NULL,
 actor_id BIGINT NOT NULL,validation_code VARCHAR(20) NOT NULL DEFAULT 'processing',reason VARCHAR(500),
 lease_token CHAR(36) NOT NULL,lease_until DATETIME NOT NULL,received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_observation_request(request_key),KEY idx_attempt_meter(meter_id,received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS observation_cursor (
 meter_id BIGINT NOT NULL,source VARCHAR(20) NOT NULL,last_valid_at DATETIME,last_valid_total DECIMAL(15,2),
 PRIMARY KEY(meter_id,source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS diagnosis_job (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,observation_id BIGINT NOT NULL,policy_version BIGINT NOT NULL,
 mode VARCHAR(20) NOT NULL,state VARCHAR(20) NOT NULL DEFAULT 'pending',attempts INT NOT NULL DEFAULT 0,
 next_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,lease_until DATETIME,lease_token CHAR(36),last_error VARCHAR(500),
 UNIQUE KEY uk_diagnosis_job(observation_id,policy_version),KEY idx_diagnosis_due(state,next_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS diagnosis_case (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,meter_id BIGINT NOT NULL,user_id BIGINT NOT NULL,family VARCHAR(40) NOT NULL,
 state VARCHAR(40) NOT NULL,severity VARCHAR(20) NOT NULL,summary VARCHAR(500) NOT NULL,mode VARCHAR(20) NOT NULL,
 policy_version BIGINT NOT NULL,model_version VARCHAR(100),anomaly_id BIGINT,work_order_id BIGINT,
 opened_at DATETIME NOT NULL,last_evidence_at DATETIME NOT NULL,closed_at DATETIME,
 KEY idx_case_meter(meter_id,opened_at),KEY idx_case_state(state,last_evidence_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS diagnosis_case_guard (
 meter_id BIGINT NOT NULL,family VARCHAR(40) NOT NULL,active_case_id BIGINT,PRIMARY KEY(meter_id,family)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS diagnosis_evidence (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,case_id BIGINT NOT NULL,observation_id BIGINT NOT NULL,window_end DATETIME NOT NULL,
 reason_codes TEXT NOT NULL,features_json TEXT NOT NULL,baseline_json TEXT NOT NULL,model_version VARCHAR(100),
 score DECIMAL(14,8),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_case_evidence(case_id,observation_id),KEY idx_evidence_case(case_id,window_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS diagnostic_probe (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,case_id BIGINT NOT NULL,sequence_no INT NOT NULL,due_at DATETIME NOT NULL,
 attempts INT NOT NULL DEFAULT 0,state VARCHAR(20) NOT NULL DEFAULT 'pending',lease_until DATETIME,
 lease_token CHAR(36),observation_id BIGINT,error VARCHAR(500),
 UNIQUE KEY uk_probe_sequence(case_id,sequence_no),KEY idx_probe_due(state,due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS diagnostic_budget (
 meter_id BIGINT NOT NULL,budget_day DATE NOT NULL,requests INT NOT NULL DEFAULT 0,PRIMARY KEY(meter_id,budget_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS diagnosis_review (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,case_id BIGINT NOT NULL,reviewer_id BIGINT NOT NULL,label VARCHAR(40) NOT NULL,
 note VARCHAR(1000) NOT NULL,request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uk_diagnosis_review(request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS work_order_verification (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,case_id BIGINT NOT NULL,work_order_id BIGINT NOT NULL,revision INT NOT NULL DEFAULT 1,
 state VARCHAR(20) NOT NULL DEFAULT 'observing',started_at DATETIME NOT NULL,deadline_at DATETIME NOT NULL,
 coverage DECIMAL(8,4) NOT NULL DEFAULT 0,policy_snapshot TEXT NOT NULL,baseline_snapshot TEXT NOT NULL,result_json TEXT,
 followup_anomaly_id BIGINT,followup_order_id BIGINT,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_verification(work_order_id,revision),KEY idx_verification_state(state,deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS diagnosis_action (
 request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,actor_id BIGINT NOT NULL,
 action VARCHAR(40) NOT NULL,case_id BIGINT NOT NULL,payload_hash CHAR(64) NOT NULL,result_json TEXT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS diagnosis_policy_audit (
 version BIGINT PRIMARY KEY,mode VARCHAR(20) NOT NULL,meter_ids TEXT NOT NULL,actor_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
