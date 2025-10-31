SELECT
    tsb.group_id,
    b.app_id,
    tsb.build_id,
    tsb.test_session_id,
    tsb.created_at
FROM raw_data.test_session_builds tsb
JOIN raw_data.builds b ON b.group_id = tsb.group_id AND b.id = tsb.build_id
WHERE b.created_at > ?
  AND b.created_at <= ?
ORDER BY tsb.created_at ASC, tsb.test_session_id ASC, tsb.build_id ASC

