INSERT INTO metrics.build_method_test_definition_coverage (
    group_id,
    app_id,
    build_id,
    method_id,
    test_session_id,
    test_definition_id,
    app_env_id,
    test_result,
    created_at_day,
    updated_at_day,
    probes
)
SELECT
    :group_id,
    :app_id,
    :build_id,
    :method_id,
    :test_session_id,
    :test_definition_id,
    :app_env_id,
    :test_result,
    :created_at_day,
    :created_at_day,
    :probes
WHERE :test_session_id IS NOT NULL AND :test_definition_id IS NOT NULL
ON CONFLICT (
    group_id,
    app_id,
    build_id,
    method_id,
    test_session_id,
    test_definition_id,
    COALESCE(app_env_id,''),
    COALESCE(test_result,'')
)
DO UPDATE
SET
    probes = build_method_test_definition_coverage.probes | EXCLUDED.probes,
    updated_at_day = EXCLUDED.created_at_day;
