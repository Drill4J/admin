DELETE FROM metrics.test_to_code_mapping
WHERE group_id = :group_id
    AND app_id = :app_id