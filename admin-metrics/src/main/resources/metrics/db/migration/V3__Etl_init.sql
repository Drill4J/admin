CREATE TABLE IF NOT EXISTS metrics.etl_metadata (
    pipeline_name VARCHAR PRIMARY KEY,
    last_processed_at TIMESTAMP WITHOUT TIME ZONE,
    last_run_at TIMESTAMP WITHOUT TIME ZONE,
    status VARCHAR,
    error_message TEXT,
    duration BIGINT,
    rows_processed BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
