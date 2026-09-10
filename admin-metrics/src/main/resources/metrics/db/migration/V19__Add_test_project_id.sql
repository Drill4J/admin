ALTER TABLE metrics.test_sessions
ADD COLUMN test_project_id VARCHAR;

ALTER TABLE metrics.test_definitions
ADD COLUMN test_project_id VARCHAR;

ALTER TABLE metrics.build_method_test_definition_coverage
ADD COLUMN test_project_id VARCHAR;

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
    COALESCE(test_result,''),
    COALESCE(test_project_id,'')
);

ALTER TABLE metrics.build_method_test_session_coverage
ADD COLUMN test_project_id VARCHAR;

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
    COALESCE(test_tag,''),
    COALESCE(test_project_id,'')
);

ALTER TABLE metrics.build_method_coverage
ADD COLUMN test_project_id VARCHAR;

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
    COALESCE(test_task_id,''),
    COALESCE(test_project_id,'')
);

ALTER TABLE metrics.method_daily_coverage
ADD COLUMN test_project_id VARCHAR;

DROP INDEX IF EXISTS metrics.method_daily_coverage_pk;
CREATE UNIQUE INDEX IF NOT EXISTS method_daily_coverage_pk ON metrics.method_daily_coverage (
    group_id,
    app_id,
    method_id,
    created_at_day,
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_result,''),
    COALESCE(test_tag,''),
    COALESCE(test_task_id,''),
    COALESCE(test_project_id,'')
);

ALTER TABLE metrics.test_to_code_mapping
ADD COLUMN test_project_id VARCHAR;

DROP INDEX IF EXISTS metrics.test_to_code_mapping_pk;
CREATE UNIQUE INDEX IF NOT EXISTS test_to_code_mapping_pk ON metrics.test_to_code_mapping (
    group_id,
    app_id,
    signature,
    test_definition_id,
    created_at_day,
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_task_id,''),
    COALESCE(test_project_id,'')
);
