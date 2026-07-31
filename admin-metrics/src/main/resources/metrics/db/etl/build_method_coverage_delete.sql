DELETE FROM metrics.build_method_coverage
WHERE group_id = :group_id
    AND app_id = :app_id
    AND build_id = :build_id