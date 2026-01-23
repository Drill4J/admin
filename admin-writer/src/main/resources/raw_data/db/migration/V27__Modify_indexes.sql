DROP INDEX IF EXISTS raw_data.idx_coverage_group_id_created_at;
DROP INDEX IF EXISTS raw_data.idx_coverage_instance_id;
DROP INDEX IF EXISTS raw_data.idx_coverage_test_id;
DROP INDEX IF EXISTS idx_instances_pk;
CREATE INDEX IF NOT EXISTS idx_method_coverage_created_at ON raw_data.method_coverage(created_at, group_id);
CREATE INDEX IF NOT EXISTS idx_method_coverage_instance_id ON raw_data.method_coverage(instance_id, app_id, group_id);
CREATE INDEX IF NOT EXISTS idx_method_coverage_signature ON raw_data.method_coverage(signature, build_id, probes_count, app_id, group_id);
CREATE INDEX IF NOT EXISTS idx_methods_signature ON raw_data.methods(signature, build_id, probes_count, app_id, group_id) INCLUDE (body_checksum);
CREATE INDEX IF NOT EXISTS idx_instances_id ON raw_data.instances(id, group_id, app_id);
