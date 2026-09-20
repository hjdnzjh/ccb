-- Additive identity and feedback schema. Select the database before execution.
CREATE TABLE IF NOT EXISTS auth_role (
 user_id BIGINT PRIMARY KEY,
 role VARCHAR(20) NOT NULL DEFAULT 'user',
 CONSTRAINT chk_auth_role CHECK (role IN ('admin','user'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO auth_role(user_id,role) SELECT id,'admin' FROM sys_user WHERE username='admin' AND deleted=0;
CREATE TABLE IF NOT EXISTS auth_session (
 token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id BIGINT NOT NULL,
 expires_at DATETIME NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_auth_session_user(user_id), KEY idx_auth_session_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS user_feedback (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, meter_id BIGINT NULL,
 subject VARCHAR(100) NOT NULL, content VARCHAR(2000) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'pending', reply VARCHAR(2000) NULL, replied_by BIGINT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, replied_at DATETIME NULL,
 KEY idx_feedback_user(user_id,created_at),
 CONSTRAINT chk_feedback_status CHECK (status IN ('pending','processing','resolved'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
