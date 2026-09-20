-- Additive migration. Run before deploying the billing/payment update.
-- Uses the database selected by the client; does not delete or synthesize history.
CREATE TABLE IF NOT EXISTS bill_payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bill_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    pay_method VARCHAR(20) NOT NULL,
    trade_no VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    paid_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bill_payment_trade (trade_no),
    KEY idx_bill_payment_bill (bill_id, id),
    CONSTRAINT chk_bill_payment_positive CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线下或演示收款登记流水';
