DELETE FROM metrics.test_to_code_mapping
WHERE group_id = :group_id
    AND (:app_id::varchar IS NULL OR app_id = :app_id)
    AND (:since_day::timestamp IS NULL OR created_at_day >= :since_day)
    AND (:until_day::timestamp IS NULL OR created_at_day < :until_day)