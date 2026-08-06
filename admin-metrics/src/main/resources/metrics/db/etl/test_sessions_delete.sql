DELETE FROM metrics.test_sessions
WHERE group_id = :group_id
    AND (:since_day::timestamp IS NULL OR created_at_day >= :since_day)
    AND (:until_day::timestamp IS NULL OR created_at_day <= :until_day)