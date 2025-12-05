CREATE INDEX IF NOT EXISTS idx_builds_created_at ON raw_data.builds(created_at);
CREATE INDEX IF NOT EXISTS idx_methods_created_at ON raw_data.methods(created_at);
CREATE INDEX IF NOT EXISTS idx_instances_created_at ON raw_data.instances(created_at);
CREATE INDEX IF NOT EXISTS idx_coverage_created_at ON raw_data.coverage(created_at);
CREATE INDEX IF NOT EXISTS idx_test_launches_created_at ON raw_data.test_launches(created_at);
CREATE INDEX IF NOT EXISTS idx_test_sessions_created_at ON raw_data.test_sessions(created_at);
CREATE INDEX IF NOT EXISTS idx_test_session_builds_created_at ON raw_data.test_session_builds(created_at);
CREATE INDEX IF NOT EXISTS idx_test_definitions_created_at ON raw_data.test_definitions(created_at);