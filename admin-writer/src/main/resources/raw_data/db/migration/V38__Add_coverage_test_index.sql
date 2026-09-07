DROP INDEX IF EXISTS idx_coverage_group_id_created_at;
CREATE INDEX IF NOT EXISTS idx_method_coverage_group_created_null_test
    ON raw_data.method_coverage (group_id, created_at)
    WHERE test_id IS NULL;

DROP INDEX IF EXISTS idx_method_coverage_test_id;
CREATE INDEX IF NOT EXISTS idx_method_coverage_test_id
    ON raw_data.method_coverage(test_id, group_id);