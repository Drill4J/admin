DELETE FROM metrics.test_session_builds
WHERE group_id = :group_id
    AND (:app_id::TEXT IS NULL OR app_id = :app_id)
    AND (:build_id::TEXT IS NULL OR build_id = :build_id)