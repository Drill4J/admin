SELECT
    c.group_id,
    c.app_id,
    i.build_id,
    i.env_id AS app_env_id,
    CASE WHEN c.test_session_id = 'GLOBAL' THEN NULL ELSE c.test_session_id END AS test_session_id,
    CASE WHEN c.test_id = 'TEST_CONTEXT_NONE' THEN NULL ELSE test_id END AS test_launch_id,
    c.method_id,
    m.signature,
    b.branch,
    tl.test_definition_id,
    test_tag,
    td.path AS test_path,
    td.name AS test_name,
    ts.test_task_id,
    tl.result AS test_result,
    c.created_at,
    DATE_TRUNC('day', c.created_at) AS created_at_day,
    c.probes AS probes
FROM raw_data.method_coverage c
JOIN raw_data.methods m ON m.method_id = c.method_id AND m.app_id = c.app_id AND m.group_id = c.group_id
JOIN raw_data.instances i ON i.id = c.instance_id
	AND i.app_id = c.app_id
	AND i.group_id = c.group_id
JOIN raw_data.builds b ON b.group_id = c.group_id AND b.app_id = c.app_id AND b.id = c.build_id
LEFT JOIN raw_data.test_sessions ts ON ts.id = c.test_session_id AND ts.group_id = c.group_id
LEFT JOIN raw_data.test_launches tl ON tl.id = c.test_id AND tl.group_id = c.group_id
LEFT JOIN raw_data.test_definitions td ON td.group_id = tl.group_id AND td.id = tl.test_definition_id
LEFT JOIN LATERAL unnest(td.tags) AS test_tag ON TRUE
WHERE c.created_at > :since_timestamp
    AND c.created_at <= :until_timestamp
    AND c.group_id = :group_id
ORDER BY c.created_at, c.method_id
LIMIT :limit