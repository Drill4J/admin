ALTER TABLE raw_data.test_sessions
    ADD COLUMN IF NOT EXISTS test_project_id VARCHAR NOT NULL DEFAULT '';
DROP INDEX IF EXISTS idx_test_sessions_pk;
CREATE INDEX idx_test_sessions_pk ON raw_data.test_sessions(group_id, test_project_id, id);

ALTER TABLE raw_data.test_launches
    ADD COLUMN IF NOT EXISTS test_project_id VARCHAR NOT NULL DEFAULT '';
DROP INDEX IF EXISTS idx_test_launches_pk;
CREATE INDEX idx_test_launches_pk ON raw_data.test_launches(group_id, test_project_id, id);


ALTER TABLE raw_data.test_definitions
    ADD COLUMN IF NOT EXISTS test_project_id VARCHAR NOT NULL DEFAULT '';
ALTER TABLE raw_data.test_definitions
    DROP CONSTRAINT IF EXISTS test_definitions_pkey;
DROP INDEX IF EXISTS raw_data.idx_test_definitions_pk;
ALTER TABLE raw_data.test_definitions
    ADD PRIMARY KEY (group_id, test_project_id, id);


