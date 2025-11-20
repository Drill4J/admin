SELECT
    tl.id AS test_launch_id,
    tl.group_id,
    tl.test_definition_id,
    tl.test_session_id,
    tl.result AS test_result,
    tl.duration AS test_duration,
    tl.created_at,
    CASE WHEN tl.result = 'PASSED' THEN 1 ELSE 0 END AS passed,
    CASE WHEN tl.result = 'FAILED' THEN 1 ELSE 0 END AS failed,
    CASE WHEN tl.result = 'SKIPPED' THEN 1 ELSE 0 END AS skipped,
    CASE WHEN tl.result = 'SMART_SKIPPED' THEN 1 ELSE 0 END AS smart_skipped,
    CASE WHEN tl.result <> 'FAILED' THEN 1 ELSE 0 END AS success,
    DATE_TRUNC('day', tl.created_at) AS created_at_day
FROM raw_data.test_launches tl
WHERE tl.created_at > :since_timestamp
    AND tl.created_at <= :until_timestamp
ORDER BY tl.created_at ASC, tl.id ASC

