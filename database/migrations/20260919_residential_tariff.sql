SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS tariff_meter_guard (
 meter_id BIGINT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS tariff_account (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL, user_id BIGINT NOT NULL,
 profile_key VARCHAR(40) NOT NULL, effective_from DATE NOT NULL,
 opening_usage DECIMAL(14,3) NOT NULL, opening_note VARCHAR(500) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS tariff_account_meter (
 meter_id BIGINT PRIMARY KEY, account_id BIGINT NOT NULL,
 KEY idx_tariff_account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS tariff_year_balance (
 account_id BIGINT NOT NULL, billing_year INT NOT NULL,
 used_amount DECIMAL(14,3) NOT NULL, last_read_at DATETIME NULL,
 PRIMARY KEY(account_id,billing_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS tariff_bill_snapshot (
 bill_id BIGINT PRIMARY KEY, reading_id BIGINT NOT NULL,
 account_id BIGINT NOT NULL, billing_year INT NOT NULL,
 before_usage DECIMAL(14,3) NOT NULL, after_usage DECIMAL(14,3) NOT NULL,
 profile_key VARCHAR(40) NOT NULL,
 UNIQUE KEY uk_tariff_reading(reading_id), KEY idx_tariff_bill_account(account_id,billing_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
