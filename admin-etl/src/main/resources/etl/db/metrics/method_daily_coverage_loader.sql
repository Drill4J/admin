INSERT INTO metrics.method_daily_coverage (
    group_id,
    app_id,
    method_id,
    branch,
    app_env_id,
    test_result,
    test_tag,
    test_task_id,
    created_at_day,
    probes
)
VALUES (
    :group_id,
    :app_id,
    :method_id,
    :branch,
    :app_env_id,
    :test_result,
    :test_tag,
    :test_task_id,
    :created_at_day,
    :probes
)
ON CONFLICT (
    group_id,
    app_id,
    method_id,
    created_at_day,
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_result,''),
    COALESCE(test_tag,''),
    COALESCE(test_task_id,'')
)
DO UPDATE
SET
    probes = method_daily_coverage.probes | EXCLUDED.probes;