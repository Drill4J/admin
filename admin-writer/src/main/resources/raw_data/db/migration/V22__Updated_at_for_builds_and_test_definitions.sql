ALTER TABLE raw_data.builds
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE raw_data.builds
SET updated_at = created_at;

ALTER TABLE raw_data.test_definitions
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE raw_data.test_definitions
SET updated_at = created_at;
