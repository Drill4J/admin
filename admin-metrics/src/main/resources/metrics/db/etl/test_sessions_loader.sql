INSERT INTO metrics.test_sessions_table (
    test_session_id,
    group_id,
    test_task_id,
    session_started_at,
    created_at,
    creation_day,
    created_by
)
VALUES (
    :test_session_id,
    :group_id,
    :test_task_id,
    :session_started_at,
    :created_at,
    :creation_day,
    :created_by
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
    creation_day = EXCLUDED.creation_day,
    created_by = EXCLUDED.created_by

