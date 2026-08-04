DELETE FROM metrics.build_method_test_session_coverage
WHERE group_id = :group_id
    AND app_id = :app_id
    AND build_id = :build_id