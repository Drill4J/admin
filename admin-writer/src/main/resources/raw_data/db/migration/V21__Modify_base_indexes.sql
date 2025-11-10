CREATE INDEX IF NOT EXISTS idx_builds_pk ON raw_data.builds(group_id, app_id, id);

DROP INDEX IF EXISTS idx_methods_build_id;
CREATE INDEX IF NOT EXISTS idx_methods_build_id_signature ON raw_data.methods(group_id, app_id, build_id, signature) WHERE probes_count > 0;
CREATE INDEX IF NOT EXISTS idx_methods_signature_build_id ON raw_data.methods(group_id, app_id, signature, build_id) WHERE probes_count > 0;
CREATE INDEX IF NOT EXISTS idx_methods_classname_name ON raw_data.methods(group_id, app_id, classname, name) WHERE probes_count > 0;

DROP INDEX IF EXISTS idx_coverage_instance_id;
CREATE INDEX IF NOT EXISTS idx_coverage_pk ON raw_data.coverage(group_id, app_id, classname, instance_id, test_id, test_session_id);

DROP INDEX IF EXISTS idx_coverage_test_id;
CREATE INDEX IF NOT EXISTS idx_coverage_test_launch_id ON raw_data.coverage(group_id, test_id);
CREATE INDEX IF NOT EXISTS idx_coverage_test_session_id ON raw_data.coverage(group_id, test_session_id);

DROP INDEX IF EXISTS idx_instances_pk;
CREATE INDEX idx_instances_pk ON raw_data.instances(group_id, app_id, id);

DROP INDEX IF EXISTS idx_test_launches_pk;
CREATE INDEX idx_test_launches_pk ON raw_data.test_launches(group_id, id);

DROP INDEX IF EXISTS idx_test_launches_test_session_id;
CREATE INDEX idx_test_launches_test_session_id ON raw_data.test_launches(group_id, test_session_id);

DROP INDEX IF EXISTS idx_test_launches_test_definition_id;
CREATE INDEX idx_test_launches_test_definition_id ON raw_data.test_launches(group_id, test_definition_id);

DROP INDEX IF EXISTS idx_test_sessions_pk;
CREATE INDEX idx_test_sessions_pk ON raw_data.test_sessions(group_id, id);

DROP INDEX IF EXISTS idx_test_definitions_pk;
CREATE INDEX idx_test_definitions_pk ON raw_data.test_definitions(group_id, id);