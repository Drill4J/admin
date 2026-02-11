ALTER TABLE metrics.etl_metadata
RENAME COLUMN duration TO load_duration;

ALTER TABLE metrics.etl_metadata
RENAME COLUMN last_duration TO last_load_duration;

ALTER TABLE metrics.etl_metadata
ADD COLUMN extract_duration BIGINT DEFAULT 0;

ALTER TABLE metrics.etl_metadata
ADD COLUMN last_extract_duration BIGINT DEFAULT 0;



