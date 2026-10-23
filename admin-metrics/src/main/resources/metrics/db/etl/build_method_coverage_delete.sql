DELETE FROM metrics.build_method_coverage
WHERE group_id = :group_id
    AND (:app_id::TEXT IS NULL OR app_id = :app_id)
    AND (:build_id::TEXT IS NULL OR build_id = :build_id)
    AND (:since_day::timestamp IS NULL OR created_at_day >= :since_day)
    AND (:until_day::timestamp IS NULL OR created_at_day < :until_day)