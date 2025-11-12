ALTER TABLE raw_data.builds
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE raw_data.builds
SET updated_at = created_at;

ALTER TABLE metrics.builds_table
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE metrics.builds_table
SET updated_at = created_at;

ALTER TABLE raw_data.test_definitions
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE raw_data.test_definitions
SET updated_at = created_at;

ALTER TABLE metrics.test_definitions_table
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE metrics.test_definitions_table
SET updated_at = created_at;
