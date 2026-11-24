-- Remove test_tag from the row-merging coverage tables (and from their unique keys).

-- metrics.build_method_coverage
DROP INDEX IF EXISTS metrics.build_method_coverage_pk;
ALTER TABLE metrics.build_method_coverage DROP COLUMN test_tag;
CREATE UNIQUE INDEX IF NOT EXISTS build_method_coverage_pk ON metrics.build_method_coverage (
    group_id,
    app_id,
    build_id,
    method_id,
    created_at_day,
    COALESCE(app_env_id,''),
    COALESCE(test_result,''),
    COALESCE(test_task_id,''),
    COALESCE(test_project_id,'')
);

-- metrics.method_daily_coverage
DROP INDEX IF EXISTS metrics.method_daily_coverage_pk;
ALTER TABLE metrics.method_daily_coverage DROP COLUMN test_tag;
CREATE UNIQUE INDEX IF NOT EXISTS method_daily_coverage_pk ON metrics.method_daily_coverage (
    group_id,
    app_id,
    method_id,
    created_at_day,
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_result,''),
    COALESCE(test_task_id,''),
    COALESCE(test_project_id,'')
);
