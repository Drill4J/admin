CREATE INDEX ON metrics.builds (updated_at_day);
CREATE INDEX ON metrics.test_launches(created_at_day);
CREATE INDEX ON metrics.test_sessions(created_at_day);
CREATE INDEX ON metrics.test_definitions(updated_at_day);
CREATE INDEX ON metrics.test_to_code_mapping(group_id, test_definition_id);