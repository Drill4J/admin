ALTER TABLE raw_data.test_sessions
    ADD COLUMN IF NOT EXISTS test_project_id VARCHAR NULL;

ALTER TABLE raw_data.test_definitions
    ADD COLUMN IF NOT EXISTS test_project_id VARCHAR NULL;

ALTER TABLE raw_data.test_launches
    ADD COLUMN IF NOT EXISTS test_project_id VARCHAR NULL;
