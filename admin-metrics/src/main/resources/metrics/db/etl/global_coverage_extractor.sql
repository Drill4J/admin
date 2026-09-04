WITH coverage AS (
	SELECT
	    c.group_id,
	    c.app_id,
		c.instance_id,
	    c.build_id,
	    c.test_session_id,
	    NULL AS test_launch_id,
	    c.method_id,
	    m.signature,
	    NULL AS test_definition_id,
	    NULL AS test_tag,
	    NULL AS test_path,
	    NULL AS test_name,
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
	WHERE c.created_at > :since_timestamp
	    AND c.created_at <= :until_timestamp
	    AND c.group_id = :group_id
	    AND c.test_id IS NULL
	ORDER BY c.created_at, c.method_id
	LIMIT :limit
)
SELECT
    c.group_id,
    c.app_id,
    c.test_launch_id,
    c.method_id,
    c.signature,
    c.test_definition_id,
    c.test_tag,
    c.test_path,
    c.test_name,
    c.test_result,
    c.created_at,
    c.created_at_day,
    c.probes AS probes,
	b.id AS build_id,
	b.branch,
    i.env_id AS app_env_id,
    ts.id AS test_session_id,
	ts.test_task_id
FROM coverage c
JOIN raw_data.instances i ON i.id = c.instance_id AND i.app_id = c.app_id AND i.group_id = c.group_id
JOIN raw_data.builds b ON b.group_id = c.group_id AND b.app_id = c.app_id AND b.id = c.build_id
LEFT JOIN raw_data.test_sessions ts ON ts.id = c.test_session_id AND ts.group_id = c.group_id
ORDER BY c.created_at, c.method_id