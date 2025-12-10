CREATE TABLE IF NOT EXISTS metrics.etl_metadata (
    pipeline_name VARCHAR NOT NULL,
    extractor_name VARCHAR NOT NULL,
    loader_name VARCHAR NOT NULL,
    last_processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_run_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status VARCHAR NOT NULL,
    error_message TEXT,
    duration BIGINT,
    rows_processed BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (pipeline_name, extractor_name, loader_name)
);
