ALTER TABLE raw_data.test_definitions
ADD COLUMN IF NOT EXISTS metadata JSON NULL;
