SELECT
    b.group_id,
    b.app_id,
    b.id AS build_id,
    split_part(b.id, ':', 3) AS version_id,
    (SELECT ARRAY_AGG(DISTINCT env_id) FROM raw_data.instances WHERE env_id != '' AND build_id = b.id) AS app_env_ids,
    b.build_version,
    b.branch,
    b.commit_sha,
    b.commit_author,
    b.commit_message,
    b.committed_at,
    DATE_TRUNC('day', b.created_at) AS creation_day,
    b.created_at
FROM raw_data.builds b
WHERE b.created_at >= metrics.get_metrics_period(b.group_id)
    AND b.created_at > :since_timestamp
    AND b.created_at <= :until_timestamp
ORDER BY b.created_at ASC