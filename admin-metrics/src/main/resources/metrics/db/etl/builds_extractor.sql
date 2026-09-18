WITH builds_with_instances AS (
    SELECT
        b.group_id,
        b.app_id,
        b.id,
        ARRAY_AGG(DISTINCT i.env_id) FILTER (WHERE i.env_id != '') AS app_env_ids,
        b.build_version,
        b.branch,
        b.commit_sha,
        b.commit_author,
        b.commit_message,
        b.committed_at,
        b.created_at,
        MIN(i.created_at) AS first_instance_created_at,
        MAX(COALESCE(i.last_heartbeat_at, i.created_at)) AS last_instance_heartbeat_at,
        GREATEST(b.updated_at, MAX(COALESCE(i.last_heartbeat_at, i.created_at))) AS updated_at
    FROM raw_data.builds b
    LEFT JOIN raw_data.instances i ON i.build_id = b.id
        AND i.group_id = b.group_id
    WHERE b.group_id = :group_id
    GROUP BY b.id
)
SELECT
    bi.group_id,
    bi.app_id,
    bi.id AS build_id,
    split_part(bi.id, ':', 3) AS version_id,
    bi.app_env_ids,
    bi.build_version,
    bi.branch,
    bi.commit_sha,
    bi.commit_author,
    bi.commit_message,
    bi.committed_at,
    bi.created_at,
    bi.first_instance_created_at,
    bi.last_instance_heartbeat_at,
    bi.updated_at,
    DATE_TRUNC('day', bi.created_at) AS created_at_day,
    DATE_TRUNC('day', bi.updated_at) AS updated_at_day
FROM builds_with_instances bi
WHERE bi.updated_at > :since_timestamp
  AND bi.updated_at <= :until_timestamp
ORDER BY bi.updated_at ASC
LIMIT :limit