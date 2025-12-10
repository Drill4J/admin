ALTER TABLE metrics.etl_metadata
ADD COLUMN group_id VARCHAR;

ALTER TABLE metrics.etl_metadata
DROP CONSTRAINT IF EXISTS etl_metadata_pkey;

INSERT INTO metrics.etl_metadata (
    group_id,
    pipeline_name,
    extractor_name,
    loader_name,
    last_processed_at,
    last_run_at,
    status,
    error_message,
    duration,
    rows_processed,
    created_at,
    updated_at
)
SELECT
    b.group_id,
    m.pipeline_name,
    m.extractor_name,
    m.loader_name,
    MIN(m.last_processed_at),
    MIN(m.last_run_at),
    MIN(m.status),
    MIN(m.error_message),
    MIN(m.duration),
    MIN(m.rows_processed),
    MIN(m.created_at),
    CURRENT_TIMESTAMP
FROM builds b
CROSS JOIN etl_metadata m
WHERE b.group_id IS NOT NULL
    AND m.group_id IS NULL
GROUP BY b.group_id, m.pipeline_name, m.extractor_name, m.loader_name;

DELETE FROM metrics.etl_metadata
WHERE group_id IS NULL;

ALTER TABLE metrics.etl_metadata
ADD PRIMARY KEY (group_id, pipeline_name, extractor_name, loader_name);