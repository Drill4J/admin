ALTER TABLE raw_data.test_launches
ADD COLUMN IF NOT EXISTS duration INT DEFAULT NULL;