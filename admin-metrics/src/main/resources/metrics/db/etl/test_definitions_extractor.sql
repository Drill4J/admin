SELECT
    td.id AS test_definition_id,
    td.group_id,
    td.path AS test_path,
    td.name AS test_name,
    td.runner AS test_runner,
    td.tags AS test_tags,
    td.metadata AS test_metadata,
    DATE_TRUNC('day', td.created_at) AS creation_day,
    td.created_at
FROM raw_data.test_definitions td
WHERE td.created_at > :since_timestamp
    AND td.created_at <= :until_timestamp
ORDER BY td.created_at ASC, td.id ASC

