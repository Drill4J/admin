INSERT INTO metrics.method_smartcoverage_table (
    group_id,
    app_id,
    method_id,
    app_env_id,
    branch,
    test_tags,
    test_task_id,
    test_result,
    creation_day,
    probes
)
VALUES (
    :group_id,
    :app_id,
    :method_id,
    :app_env_id,
    :branch,
    :test_tags,
    :test_task_id,
    :test_result,
    :creation_day,
    :probes
)
ON CONFLICT (
    group_id,
    app_id,
    method_id,
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_tags,'{}'),
    COALESCE(test_task_id,''),
    COALESCE(test_result,''),
    creation_day
)
DO UPDATE
SET
    probes = method_smartcoverage_table.probes | EXCLUDED.probes

