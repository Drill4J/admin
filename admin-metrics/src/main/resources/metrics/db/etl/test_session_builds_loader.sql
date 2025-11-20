INSERT INTO metrics.test_session_builds (
    group_id,
    app_id,
    build_id,
    test_session_id,
    created_at_day,
    updated_at_day
)
SELECT
    :group_id,
    :app_id,
    :build_id,
    :test_session_id,
    :created_at_day,
    :created_at_day
WHERE :test_session_id IS NOT NULL
ON CONFLICT (
    group_id,
    app_id,
    build_id,
    test_session_id
)
DO UPDATE SET
    updated_at_day = EXCLUDED.created_at_day;