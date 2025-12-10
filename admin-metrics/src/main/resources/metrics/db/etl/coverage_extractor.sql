SELECT
    c.group_id,
    c.app_id,
    c.build_id,
    c.app_env_id,
    c.test_session_id,
    c.test_launch_id,
    c.method_id,
    c.signature,
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
FROM raw_data.view_methods_coverage_v4 c
JOIN raw_data.builds b ON b.group_id = c.group_id AND b.app_id = c.app_id AND b.id = c.build_id
LEFT JOIN raw_data.test_sessions ts ON ts.group_id = c.group_id AND ts.id = c.test_session_id
LEFT JOIN raw_data.test_launches tl ON tl.group_id = c.group_id AND tl.id = c.test_launch_id
LEFT JOIN raw_data.test_definitions td ON td.group_id = tl.group_id AND td.id = tl.test_definition_id
LEFT JOIN LATERAL unnest(td.tags) AS test_tag ON TRUE
WHERE c.created_at > :since_timestamp
    AND c.created_at <= :until_timestamp
ORDER BY c.created_at ASC, c.method_id ASC