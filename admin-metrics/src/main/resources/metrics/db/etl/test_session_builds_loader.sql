INSERT INTO metrics.test_session_builds (
    group_id,
    app_id,
    build_id,
    test_session_id,
    created_at_day
)
VALUES (
    :group_id,
    :app_id,
    :build_id,
    :test_session_id,
    :created_at_day
)
ON CONFLICT (
    group_id,
    app_id,
    build_id,
    test_session_id
)
DO NOTHING