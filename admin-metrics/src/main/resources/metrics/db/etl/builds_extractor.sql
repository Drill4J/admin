SELECT
    b.group_id,
    b.app_id,
    b.id AS build_id,
    split_part(b.id, ':', 3) AS version_id,
    ARRAY_AGG(DISTINCT i.env_id) FILTER (WHERE i.env_id != '') AS app_env_ids,
    b.build_version,
    b.branch,
    b.commit_sha,
    b.commit_author,
    b.commit_message,
    b.committed_at,
    b.created_at,
    GREATEST(b.updated_at, MAX(i.created_at)) AS updated_at,
    DATE_TRUNC('day', b.created_at) AS created_at_day,
    DATE_TRUNC('day', GREATEST(b.updated_at, MAX(i.created_at))) AS updated_at_day
FROM raw_data.builds b
LEFT JOIN raw_data.instances i ON i.build_id = b.id
	AND i.group_id = b.group_id
	AND i.created_at > :since_timestamp
	AND i.created_at <= :until_timestamp
WHERE b.group_id = :group_id
    AND b.updated_at > :since_timestamp
    AND b.updated_at <= :until_timestamp
GROUP BY b.id
ORDER BY GREATEST(b.updated_at, MAX(i.created_at)) ASC
LIMIT :limit