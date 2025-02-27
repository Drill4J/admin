CREATE INDEX IF NOT EXISTS idx_test_launches_pk ON raw_data.test_launches(id);
CREATE INDEX IF NOT EXISTS idx_coverage_test_id ON raw_data.coverage(test_id);
CREATE INDEX IF NOT EXISTS idx_methods_build_id ON raw_data.methods(build_id) WHERE probes_count > 0;
CREATE INDEX IF NOT EXISTS idx_instances_pk ON raw_data.instances(id);
CREATE INDEX IF NOT EXISTS idx_coverage_instance_id ON raw_data.coverage(instance_id);
CREATE INDEX IF NOT EXISTS idx_test_launches_test_session_id ON raw_data.test_launches(test_session_id);
CREATE INDEX IF NOT EXISTS idx_test_sessions_pk ON raw_data.test_sessions(id);
CREATE INDEX IF NOT EXISTS idx_test_launches_test_definition_id ON raw_data.test_launches(test_definition_id);
CREATE INDEX IF NOT EXISTS idx_test_definitions_pk ON raw_data.test_definitions(id);