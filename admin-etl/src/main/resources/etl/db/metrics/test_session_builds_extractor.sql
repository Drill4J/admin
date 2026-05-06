SELECT
    tsb.group_id,
    b.app_id,
    tsb.build_id,
    tsb.test_session_id,
    tsb.created_at,
    DATE_TRUNC('day', tsb.created_at) AS created_at_day
FROM raw_data.test_session_builds tsb
JOIN raw_data.builds b ON b.group_id = tsb.group_id AND b.id = tsb.build_id
WHERE b.group_id = :group_id
    AND b.created_at > :since_timestamp
    AND b.created_at <= :until_timestamp
ORDER BY tsb.created_at ASC, tsb.test_session_id ASC, tsb.build_id ASC
LIMIT :limit
