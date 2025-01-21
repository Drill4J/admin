CREATE TABLE IF NOT EXISTS raw_data.group_settings (
    group_id VARCHAR NOT NULL,
    retention_period_days INT NULL,
    PRIMARY KEY (group_id)
);