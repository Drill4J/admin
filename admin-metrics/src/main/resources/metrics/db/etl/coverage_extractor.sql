SELECT
    c.group_id,
    c.app_id,
    MD5(c.signature||':'||c.body_checksum||':'||c.probes_count) AS method_id,
    c.build_id,
    c.env_id AS app_env_id,
    c.test_session_id,
    c.test_launch_id,
    MIN(b.branch) AS branch,
    MIN(td.tags) AS test_tags,
    MIN(td.path) AS test_path,
    MIN(td.name) AS test_name,
    MIN(ts.test_task_id) AS test_task_id,
    MIN(tl.result) AS test_result,
    MIN(tl.test_definition_id) AS test_definition_id,
    BIT_OR(c.probes) AS probes,
    DATE_TRUNC('day', c.created_at) AS creation_day,
    MAX(c.created_at) AS created_at
FROM raw_data.view_methods_coverage_v2 c
JOIN raw_data.builds b ON b.group_id = c.group_id AND b.app_id = c.app_id AND b.id = c.build_id
LEFT JOIN raw_data.test_launches tl ON tl.group_id = c.group_id AND tl.id = c.test_launch_id
LEFT JOIN raw_data.test_definitions td ON td.group_id = tl.group_id AND td.id = tl.test_definition_id
LEFT JOIN raw_data.test_sessions ts ON ts.group_id = c.group_id AND ts.id = c.test_session_id
WHERE c.created_at > :since_timestamp
    AND c.created_at <= :until_timestamp
GROUP BY c.group_id, c.app_id, c.signature, c.body_checksum, c.probes_count, c.build_id, c.env_id, c.test_session_id, c.test_launch_id, DATE_TRUNC('day', c.created_at)
ORDER BY MAX(c.created_at) ASC, c.signature ASC