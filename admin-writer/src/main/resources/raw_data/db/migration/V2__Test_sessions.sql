CREATE TABLE IF NOT EXISTS raw_data.test_sessions (
    id VARCHAR PRIMARY KEY,
    group_id VARCHAR,
    test_task_id VARCHAR,
    started_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE raw_data.test_launches
ADD COLUMN test_session_id VARCHAR NULL;

ALTER TABLE raw_data.test_launches
DROP COLUMN IF EXISTS test_task_id;
