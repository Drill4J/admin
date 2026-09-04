-- Add created_at_day to the unique keys of the row-merging coverage tables so that
-- coverage is partitioned per day. This enables day-scoped deletion and rerun.
-- created_at_day is always populated (DATE_TRUNC('day', created_at)), so it is added
-- as a plain column (no COALESCE needed).

-- metrics.build_method_coverage
DROP INDEX IF EXISTS metrics.build_method_coverage_pk;
CREATE UNIQUE INDEX IF NOT EXISTS build_method_coverage_pk ON metrics.build_method_coverage (
    group_id,
    app_id,
    build_id,
    method_id,
    created_at_day,
    COALESCE(app_env_id,''),
    COALESCE(test_result,''),
    COALESCE(test_tag,''),
    COALESCE(test_task_id,'')
);

-- metrics.build_method_test_definition_coverage
DROP INDEX IF EXISTS metrics.build_method_test_definition_coverage_pk;
CREATE UNIQUE INDEX IF NOT EXISTS build_method_test_definition_coverage_pk ON metrics.build_method_test_definition_coverage (
    group_id,
    app_id,
    build_id,
    method_id,
    test_session_id,
    test_definition_id,
    created_at_day,
    COALESCE(app_env_id,''),
    COALESCE(test_result,'')
);

-- metrics.build_method_test_session_coverage
DROP INDEX IF EXISTS metrics.build_method_test_session_coverage_pk;
CREATE UNIQUE INDEX IF NOT EXISTS build_method_test_session_coverage_pk ON metrics.build_method_test_session_coverage (
    group_id,
    app_id,
    build_id,
    method_id,
    test_session_id,
    created_at_day,
    COALESCE(app_env_id,''),
    COALESCE(test_result,''),
    COALESCE(test_tag,'')
);

-- metrics.test_to_code_mapping
DROP INDEX IF EXISTS metrics.test_to_code_mapping_pk;
CREATE UNIQUE INDEX IF NOT EXISTS test_to_code_mapping_pk ON metrics.test_to_code_mapping (
    group_id,
    app_id,
    signature,
    test_definition_id,
    created_at_day,
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_task_id,'')
);
