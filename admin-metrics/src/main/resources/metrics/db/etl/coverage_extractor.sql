WITH coverage AS (
    SELECT
        c.group_id,
        c.app_id,
        c.instance_id,
        c.build_id,
        b.branch,
        c.test_session_id,
        tl.id AS test_launch_id,
        tl.test_definition_id,
        tl.result AS test_result,
        tl.created_at AS test_completed_at,
        c.method_id,
        m.signature,
        c.created_at,
        c.updated_at,
        c.probes
    FROM raw_data.method_coverage c
    JOIN raw_data.builds b ON b.group_id = c.group_id AND b.app_id = c.app_id AND b.id = c.build_id
        AND b.validation_status = 'VALID'
    JOIN raw_data.methods m ON m.method_id = c.method_id AND m.app_id = c.app_id AND m.group_id = c.group_id
        AND NOT EXISTS (
            SELECT 1
            FROM raw_data.method_ignore_rules r
            WHERE r.group_id = m.group_id
              AND r.app_id = m.app_id
              AND (r.classname_pattern IS NULL OR m.class_name::text ~ r.classname_pattern::text)
              AND (r.name_pattern IS NULL OR m.method_name::text ~ r.name_pattern::text)
        )
    LEFT JOIN raw_data.test_launches tl ON tl.group_id = c.group_id AND tl.id = c.test_id
    WHERE c.updated_at > :since_timestamp
        AND c.updated_at <= :until_timestamp
        AND c.group_id = :group_id
        AND (:app_id::TEXT IS NULL OR c.app_id = :app_id)
        AND (:build_id::TEXT IS NULL OR c.build_id = :build_id)
        AND (:test_session_id::TEXT IS NULL OR c.test_session_id = :test_session_id)
        AND (:test_definition_id::TEXT IS NULL OR tl.test_definition_id = :test_definition_id)
        AND (c.test_id IS NULL OR tl.id IS NOT NULL)
    ORDER BY c.updated_at, c.method_id
    LIMIT :limit
)
SELECT
    c.group_id,
    c.app_id,
    c.build_id,
    c.branch,
    i.env_id AS app_env_id,
    c.test_session_id,
    ts.test_task_id,
    ts.test_project_id,
    c.test_launch_id,
    c.test_definition_id,
    c.test_result,
    c.test_completed_at,
    td.path AS test_path,
    td.name AS test_name,
    test_tag,
    c.method_id,
    c.signature,
    c.created_at,
    c.updated_at,
    DATE_TRUNC('day', c.updated_at) AS created_at_day,
    c.probes
FROM coverage c
JOIN raw_data.instances i ON i.id = c.instance_id AND i.app_id = c.app_id AND i.group_id = c.group_id
LEFT JOIN raw_data.test_sessions ts ON ts.id = c.test_session_id AND ts.group_id = c.group_id
LEFT JOIN raw_data.test_definitions td ON td.id = c.test_definition_id AND td.group_id = c.group_id
LEFT JOIN LATERAL unnest(td.tags) AS test_tag ON TRUE
ORDER BY c.updated_at, c.method_id