ALTER TABLE metrics.etl_metadata
ADD COLUMN last_duration BIGINT DEFAULT 0;

ALTER TABLE metrics.etl_metadata
ADD COLUMN last_rows_processed BIGINT DEFAULT 0;

ALTER TABLE metrics.etl_metadata
ALTER COLUMN duration SET DEFAULT 0;

ALTER TABLE metrics.etl_metadata
ALTER COLUMN rows_processed SET DEFAULT 0;

UPDATE metrics.etl_metadata
SET duration = 0
WHERE duration IS NULL;

UPDATE metrics.etl_metadata
SET rows_processed = 0
WHERE rows_processed IS NULL;