-- Per-account read receipts. Business records remain the source of truth.
CREATE TABLE IF NOT EXISTS notification_read (
 user_id BIGINT NOT NULL,
 message_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,message_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
