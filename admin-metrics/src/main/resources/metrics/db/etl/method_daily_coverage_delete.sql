DELETE FROM metrics.method_daily_coverage
WHERE group_id = :group_id
    AND (:since_day::timestamp IS NULL OR created_at_day >= :since_day)
    AND (:until_day::timestamp IS NULL OR created_at_day <= :until_day)