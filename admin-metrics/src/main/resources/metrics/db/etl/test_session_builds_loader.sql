INSERT INTO metrics.test_session_builds_table (
    group_id,
    app_id,
    build_id,
    test_session_id
)
SELECT
    :group_id,
    :app_id,
    :build_id,
    :test_session_id
WHERE :test_session_id IS NOT NULL
ON CONFLICT (
    group_id,
    app_id,
    build_id,
    test_session_id
)
DO NOTHING