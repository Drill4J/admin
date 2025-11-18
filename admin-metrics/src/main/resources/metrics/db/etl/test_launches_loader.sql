INSERT INTO metrics.test_launches_table (
    test_launch_id,
    group_id,
    test_definition_id,
    test_session_id,
    test_result,
    test_duration,
    created_at,
    created_at_day,
    passed,
    failed,
    skipped,
    smart_skipped,
    success
)
VALUES (
    :test_launch_id,
    :group_id,
    :test_definition_id,
    :test_session_id,
    :test_result,
    :test_duration,
    :created_at,
    :created_at_day,
    :passed,
    :failed,
    :skipped,
    :smart_skipped,
    :success
)
ON CONFLICT (
    group_id,
    test_launch_id
)
DO UPDATE
SET
    test_definition_id = EXCLUDED.test_definition_id,
    test_session_id = EXCLUDED.test_session_id,
    test_result = EXCLUDED.test_result,
    test_duration = EXCLUDED.test_duration,
    created_at = EXCLUDED.created_at,
    created_at_day = EXCLUDED.created_at_day,
    passed = EXCLUDED.passed,
    failed = EXCLUDED.failed,
    skipped = EXCLUDED.skipped,
    smart_skipped = EXCLUDED.smart_skipped,
    success = EXCLUDED.success

