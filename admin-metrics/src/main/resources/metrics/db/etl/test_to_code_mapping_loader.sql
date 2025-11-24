INSERT INTO metrics.test_to_code_mapping(
    group_id,
    app_id,
    signature,
    test_definition_id,
    branch,
    app_env_id,
    test_task_id,
    created_at_day,
    updated_at_day
)
VALUES (
    :group_id,
    :app_id,
    :signature,
    :test_definition_id,
    :branch,
    :app_env_id,
    :test_task_id,
    :created_at_day,
    :created_at_day
)
ON CONFLICT (
    group_id,
    app_id,
    signature,
    test_definition_id,
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_task_id,'')
)
DO UPDATE SET
    updated_at_day = EXCLUDED.created_at_day