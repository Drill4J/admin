INSERT INTO metrics.test_to_code_mapping_table(
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
SELECT
    :group_id,
    :app_id,
    :signature,
    :test_definition_id,
    :branch,
    :app_env_id,
    :test_task_id,
    :created_at_day,
    :created_at_day
WHERE :test_definition_id IS NOT NULL AND :test_result = 'PASSED'
ON CONFLICT (
    group_id,
    app_id,
    signature,
    test_definition_id
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_task_id,'')
)
DO UPDATE SET
    updated_at_day = EXCLUDED.created_at_day