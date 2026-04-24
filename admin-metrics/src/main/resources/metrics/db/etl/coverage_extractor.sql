SELECT
    c.group_id,
    c.app_id,
    i.build_id,
    i.env_id AS app_env_id,
    ts.id AS test_session_id,
    NULL AS test_launch_id,
    c.method_id,
    m.signature,
    b.branch,
    NULL AS test_definition_id,
    NULL AS test_tag,
    NULL AS test_path,
    NULL AS test_name,
    ts.test_task_id,
    NULL AS test_result,
    c.created_at,
    DATE_TRUNC('day', c.created_at) AS created_at_day,
    c.probes AS probes
FROM raw_data.method_coverage c
JOIN raw_data.methods m ON m.method_id = c.method_id AND m.app_id = c.app_id AND m.group_id = c.group_id
    AND NOT EXISTS (
            SELECT 1
            FROM raw_data.method_ignore_rules r
            WHERE r.group_id = m.group_id
                AND r.app_id = m.app_id
                AND (r.classname_pattern IS NOT NULL AND m.class_name::text ~ r.classname_pattern::text
                    OR r.name_pattern IS NOT NULL AND m.method_name::text ~ r.name_pattern::text)
        )
JOIN raw_data.instances i ON i.id = c.instance_id AND i.app_id = c.app_id AND i.group_id = c.group_id
JOIN raw_data.builds b ON b.group_id = c.group_id AND b.app_id = c.app_id AND b.id = c.build_id
LEFT JOIN raw_data.test_sessions ts ON ts.id = c.test_session_id AND ts.group_id = c.group_id
WHERE c.created_at > :since_timestamp
    AND c.created_at <= :until_timestamp
    AND c.group_id = :group_id
    AND (c.test_id IS NULL OR c.test_id = 'TEST_CONTEXT_NONE')
ORDER BY c.created_at, c.method_id
LIMIT :limit