CREATE INDEX IF NOT EXISTS idx_builds_pk ON raw_data.builds(group_id, app_id, id);

DROP INDEX IF EXISTS idx_methods_build_id;
--Used in raw_data.view_methods_with_rules
CREATE INDEX IF NOT EXISTS idx_methods_classname ON raw_data.methods(group_id, app_id, classname, name, annotations, class_annotations) WHERE probes_count > 0;
--Used in metrics.build_method_test_launch_coverage_view
CREATE INDEX IF NOT EXISTS idx_methods_build_id_classname ON raw_data.methods(group_id, app_id, build_id, classname) WHERE probes_count > 0;

DROP INDEX IF EXISTS idx_coverage_instance_id;
--Used in metrics.build_method_test_launch_coverage_view
CREATE INDEX IF NOT EXISTS idx_coverage_instance_id ON raw_data.coverage(group_id, app_id, instance_id, classname, test_session_id, test_id);
CREATE INDEX IF NOT EXISTS idx_coverage_created_at ON raw_data.coverage(group_id, created_at);

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