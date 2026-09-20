-- Additive and repeatable. Uses the database selected by the operator.
CREATE TABLE IF NOT EXISTS report_snapshot (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    generated_at DATETIME NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    granularity VARCHAR(10) NOT NULL,
    snapshot_json JSON NOT NULL,
    PRIMARY KEY (id),
    KEY idx_report_generated (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable report snapshots for screen and export';
