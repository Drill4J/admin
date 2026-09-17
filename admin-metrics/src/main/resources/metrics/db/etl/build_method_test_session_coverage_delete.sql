DELETE FROM metrics.build_method_test_session_coverage
WHERE group_id = :group_id
    AND (:test_session_id::TEXT IS NULL OR test_session_id = :test_session_id)
    AND (:since_day::timestamp IS NULL OR created_at_day >= :since_day)
    AND (:until_day::timestamp IS NULL OR created_at_day < :until_day)