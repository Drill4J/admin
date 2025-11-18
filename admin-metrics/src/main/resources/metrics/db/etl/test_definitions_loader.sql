INSERT INTO metrics.test_definitions_table (
    test_definition_id,
    group_id,
    test_path,
    test_name,
    test_runner,
    test_tags,
    test_metadata,
    created_at,
    updated_at,
    created_at_day,
    updated_at_day
)
VALUES (
    :test_definition_id,
    :group_id,
    :test_path,
    :test_name,
    :test_runner,
    :test_tags,
    :test_metadata,
    :created_at,
    :updated_at,
    :created_at_day,
    :updated_at_day
)
ON CONFLICT (
    group_id,
    test_definition_id
)
DO UPDATE
SET
    test_path = EXCLUDED.test_path,
    test_name = EXCLUDED.test_name,
    test_runner = EXCLUDED.test_runner,
    test_tags = EXCLUDED.test_tags,
    test_metadata = EXCLUDED.test_metadata,
    updated_at = EXCLUDED.updated_at,
    updated_at_day = EXCLUDED.updated_at_day
