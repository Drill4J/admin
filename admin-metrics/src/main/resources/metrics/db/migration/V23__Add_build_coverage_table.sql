CREATE TABLE IF NOT EXISTS metrics.build_coverage (
    group_id        VARCHAR,
    app_id          VARCHAR,
    build_id        VARCHAR,
    app_env_id      VARCHAR,
    test_result     VARCHAR,
    test_session_id VARCHAR,
    test_project_id VARCHAR DEFAULT '',
    created_at_day  TIMESTAMP WITHOUT TIME ZONE,
    updated_at_day  TIMESTAMP WITHOUT TIME ZONE,
    probes          VARBIT
);
CREATE UNIQUE INDEX IF NOT EXISTS build_coverage_pk ON metrics.build_coverage (
    group_id, app_id, build_id, created_at_day,
    COALESCE(app_env_id, ''), COALESCE(test_result, ''), COALESCE(test_session_id, ''),
    COALESCE(test_project_id, '')
);
