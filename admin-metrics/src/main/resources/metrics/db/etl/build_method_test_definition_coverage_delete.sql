DELETE FROM metrics.build_method_test_definition_coverage
WHERE group_id = :group_id
    AND app_id = :app_id
    AND build_id = :build_id
    AND (:test_session_id::TEXT IS NULL OR test_session_id = :test_session_id)
    AND (:test_definition_id::TEXT IS NULL OR test_definition_id = :test_definition_id)