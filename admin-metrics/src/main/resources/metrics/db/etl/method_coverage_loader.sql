INSERT INTO metrics.method_coverage_table (
    group_id,
    app_id,
    method_id,
    build_id,
    app_env_id,
    test_session_id,
    test_launch_id,
    branch,
    test_tags,
    test_path,
    test_name,
    test_task_id,
    test_result,
    test_definition_id,
    probes,
    creation_day
)
VALUES (
    :group_id,
    :app_id,
    :method_id,
    :build_id,
    :app_env_id,
    :test_session_id,
    :test_launch_id,
    :branch,
    :test_tags,
    :test_path,
    :test_name,
    :test_task_id,
    :test_result,
    :test_definition_id,
    :probes,
    :creation_day
)
ON CONFLICT (
    group_id,
    app_id,
    method_id,
    build_id,
    COALESCE(app_env_id,''),
    COALESCE(test_session_id,''),
    COALESCE(test_launch_id,''),
    creation_day
)
DO UPDATE
SET
    probes = method_coverage_table.probes | EXCLUDED.probes