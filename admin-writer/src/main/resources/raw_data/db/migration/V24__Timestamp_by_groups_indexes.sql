DROP INDEX IF EXISTS raw_data.idx_builds_created_at;
DROP INDEX IF EXISTS raw_data.idx_methods_created_at;
DROP INDEX IF EXISTS raw_data.idx_instances_created_at;
DROP INDEX IF EXISTS raw_data.idx_coverage_created_at;
DROP INDEX IF EXISTS raw_data.idx_test_launches_created_at;
DROP INDEX IF EXISTS raw_data.idx_test_sessions_created_at;
DROP INDEX IF EXISTS raw_data.idx_test_session_builds_created_at;
DROP INDEX IF EXISTS raw_data.idx_test_definitions_created_at;

CREATE INDEX IF NOT EXISTS idx_builds_group_id_created_at ON raw_data.builds(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_methods_group_id_created_at ON raw_data.methods(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_instances_group_id_created_at ON raw_data.instances(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_coverage_group_id_created_at ON raw_data.coverage(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_test_launches_group_id_created_at ON raw_data.test_launches(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_test_sessions_group_id_created_at ON raw_data.test_sessions(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_test_session_builds_group_id_created_at ON raw_data.test_session_builds(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_test_definitions_group_id_created_at ON raw_data.test_definitions(group_id, created_at);