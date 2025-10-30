SELECT
    ts.id AS test_session_id,
    ts.group_id,
    ts.test_task_id,
    ts.started_at AS session_started_at,
    DATE_TRUNC('day', ts.created_at) AS creation_day,
    ts.created_by,
    ts.created_at
FROM raw_data.test_sessions ts
WHERE ts.created_at > ?
    AND ts.created_at <= ?
ORDER BY ts.created_at ASC, ts.id ASC

