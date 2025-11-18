INSERT INTO metrics.test_sessions_table (
    test_session_id,
    group_id,
    test_task_id,
    session_started_at,
    created_at,
    created_by,
    created_at_day
)
VALUES (
    :test_session_id,
    :group_id,
    :test_task_id,
    :session_started_at,
    :created_at,
    :created_by,
    :created_at_day
)
ON CONFLICT (
    group_id,
    test_session_id
)
DO UPDATE
SET
    test_task_id = EXCLUDED.test_task_id,
    session_started_at = EXCLUDED.session_started_at,
    created_at = EXCLUDED.created_at,
    created_by = EXCLUDED.created_by,
    created_at_day = EXCLUDED.created_at_day

