-- Stale rows left with the new names before pipelines were renamed in V28
DELETE FROM metrics.etl_metadata
WHERE pipeline_name IN (
    'build_method_coverage',
    'build_coverage',
    'method_daily_coverage',
    'test_to_code_mapping',
    'test_session_builds_from_coverage',
    'test_session_coverage',
    'test_definition_coverage'
);

WITH pipeline_mapping(old_name, new_name) AS (
    VALUES
        ('incremental_build_method_coverage', 'build_method_coverage'),
        ('incremental_build_method_coverage_from_test_launches', 'build_method_coverage'),
        ('historical_build_method_coverage', 'build_method_coverage'),

        ('incremental_build_coverage', 'build_coverage'),
        ('incremental_build_coverage_from_test_launches', 'build_coverage'),
        ('historical_build_coverage', 'build_coverage'),

        ('incremental_method_daily_coverage', 'method_daily_coverage'),
        ('incremental_method_daily_coverage_from_test_launches', 'method_daily_coverage'),
        ('historical_method_daily_coverage', 'method_daily_coverage'),

        ('incremental_test_to_code_mapping_from_test_launches', 'test_to_code_mapping'),
        ('historical_test_to_code_mapping', 'test_to_code_mapping'),

        ('incremental_test_session_builds', 'test_session_builds_from_coverage'),
        ('incremental_test_session_builds_from_test_launches', 'test_session_builds_from_coverage'),
        ('historical_test_session_builds', 'test_session_builds_from_coverage'),

        ('incremental_test_session_coverage', 'test_session_coverage'),
        ('incremental_test_session_coverage_from_test_launches', 'test_session_coverage'),

        ('incremental_test_definition_coverage_from_test_launches', 'test_definition_coverage')
),
merged AS (
    SELECT DISTINCT ON (pm.new_name, m.group_id, m.app_id, m.build_id, m.test_session_id, m.period_from, m.period_to)
        pm.new_name AS pipeline_name,
        m.group_id,
        m.app_id,
        m.build_id,
        m.test_session_id,
        m.period_from,
        m.period_to,
        m.loader_name,
        m.status,
        m.last_processed_at,
        m.last_run_at,
        m.last_extract_duration,
        m.last_load_duration,
        m.last_rows_processed,
        m.extract_duration,
        m.load_duration,
        m.rows_processed,
        m.error_message,
        m.created_at,
        m.updated_at
    FROM metrics.etl_metadata m
    JOIN pipeline_mapping pm ON pm.old_name = m.pipeline_name
    ORDER BY pm.new_name, m.group_id, m.app_id, m.build_id, m.test_session_id, m.period_from, m.period_to,
             m.last_processed_at ASC
)
INSERT INTO metrics.etl_metadata (
    pipeline_name,
    group_id,
    app_id,
    build_id,
    test_session_id,
    period_from,
    period_to,
    extractor_name,
    loader_name,
    status,
    last_processed_at,
    last_run_at,
    last_extract_duration,
    last_load_duration,
    last_rows_processed,
    extract_duration,
    load_duration,
    rows_processed,
    error_message,
    created_at,
    updated_at
)
SELECT
    pipeline_name,
    group_id,
    app_id,
    build_id,
    test_session_id,
    period_from,
    period_to,
    'coverage',
    loader_name,
    status,
    last_processed_at,
    last_run_at,
    last_extract_duration,
    last_load_duration,
    last_rows_processed,
    extract_duration,
    load_duration,
    rows_processed,
    error_message,
    created_at,
    updated_at
FROM merged
ON CONFLICT DO NOTHING;

DELETE FROM metrics.etl_metadata
WHERE pipeline_name IN (
    'incremental_build_method_coverage',
    'incremental_build_method_coverage_from_test_launches',
    'historical_build_method_coverage',
    'incremental_build_coverage',
    'incremental_build_coverage_from_test_launches',
    'historical_build_coverage',
    'incremental_method_daily_coverage',
    'incremental_method_daily_coverage_from_test_launches',
    'historical_method_daily_coverage',
    'incremental_test_to_code_mapping_from_test_launches',
    'historical_test_to_code_mapping',
    'incremental_test_session_builds',
    'incremental_test_session_builds_from_test_launches',
    'historical_test_session_builds',
    'incremental_test_session_coverage',
    'incremental_test_session_coverage_from_test_launches',
    'incremental_test_definition_coverage_from_test_launches'
);
