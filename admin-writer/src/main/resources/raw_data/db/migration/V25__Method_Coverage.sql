CREATE IF NOT EXISTS TABLE raw_data.method_coverage(
    id SERIAL PRIMARY KEY,
    instance_id VARCHAR,
    signature VARCHAR,
    test_id VARCHAR,
    probes VARBIT,
    probes_count INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    group_id VARCHAR NOT NULL,
    app_id VARCHAR NOT NULL,
    test_session_id VARCHAR
);

CREATE INDEX IF NOT EXISTS idx_coverage_group_id_created_at ON raw_data.method_coverage(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_coverage_instance_id ON raw_data.method_coverage(group_id, app_id, instance_id, signature, test_session_id, test_id);
CREATE INDEX IF NOT EXISTS idx_coverage_test_id ON raw_data.method_coverage(test_id);