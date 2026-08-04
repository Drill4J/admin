DELETE FROM metrics.method_daily_coverage
WHERE group_id = :group_id
    AND app_id = :app_id