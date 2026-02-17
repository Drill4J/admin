SELECT
    td.id AS test_definition_id,
    td.group_id,
    td.path AS test_path,
    td.name AS test_name,
    td.runner AS test_runner,
    td.tags AS test_tags,
    td.metadata AS test_metadata,
    td.created_at,
    td.updated_at,
    DATE_TRUNC('day', td.created_at) AS created_at_day,
    DATE_TRUNC('day', td.updated_at) AS updated_at_day
FROM raw_data.test_definitions td
WHERE td.group_id = :group_id
    AND td.updated_at > :since_timestamp
    AND td.updated_at <= :until_timestamp
ORDER BY td.updated_at ASC, td.id ASC
LIMIT :limit