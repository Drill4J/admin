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
    b.created_at,
    b.updated_at,
    DATE_TRUNC('day', b.created_at) AS created_at_day,
    DATE_TRUNC('day', b.updated_at) AS updated_at_day
FROM raw_data.builds b
WHERE b.group_id = :group_id
    AND b.updated_at > :since_timestamp
    AND b.updated_at <= :until_timestamp
ORDER BY b.updated_at ASC