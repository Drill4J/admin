INSERT INTO metrics.build_coverage (
    group_id,
    app_id,
    build_id,
    app_env_id,
    test_result,
    test_session_id,
    test_project_id,
    created_at_day,
    updated_at_day,
    code_probes,
    method_probes
)
VALUES (
    :group_id,
    :app_id,
    :build_id,
    :app_env_id,
    :test_result,
    :test_session_id,
    :test_project_id,
    :created_at_day,
    :created_at_day,
    :code_probes,
    :method_probes
)
ON CONFLICT (
    group_id,
    app_id,
    build_id,
    created_at_day,
    COALESCE(app_env_id, ''),
    COALESCE(test_result, ''),
    COALESCE(test_session_id, ''),
    COALESCE(test_project_id, '')
)
DO UPDATE
SET
    code_probes = build_coverage.code_probes | EXCLUDED.code_probes,
    method_probes = build_coverage.method_probes | EXCLUDED.method_probes
WHERE build_coverage.code_probes IS DISTINCT FROM EXCLUDED.code_probes
   OR build_coverage.method_probes IS DISTINCT FROM EXCLUDED.method_probes;