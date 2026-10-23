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
        b.validation_status,
        b.agent_version,
        b.agent_env,
        b.agent_params,
        b.created_at,
        MIN(i.created_at) AS first_instance_created_at,
        MAX(COALESCE(i.last_heartbeat_at, i.created_at)) AS last_instance_heartbeat_at,
        GREATEST(b.updated_at, MAX(COALESCE(i.last_heartbeat_at, i.created_at))) AS updated_at
    FROM raw_data.builds b
    LEFT JOIN raw_data.instances i ON i.build_id = b.id
        AND i.group_id = b.group_id
    WHERE b.group_id = :group_id
    GROUP BY b.group_id, b.app_id, b.id
),
build_totals AS (
    SELECT
        b.group_id,
        b.app_id,
        b.id AS build_id,
        COUNT(DISTINCT m.class_name) AS total_classes,
        COUNT(DISTINCT m.signature)  AS total_methods,
        SUM(m.probes_count)          AS total_probes
    FROM raw_data.builds b
    JOIN raw_data.build_methods bm
        ON bm.group_id = b.group_id
       AND bm.app_id   = b.app_id
       AND bm.build_id = b.id
    JOIN raw_data.methods m
        ON m.method_id  = bm.method_id
       AND m.app_id     = bm.app_id
       AND m.group_id   = bm.group_id
       AND m.probes_count > 0
       AND NOT EXISTS (
           SELECT 1
           FROM raw_data.method_ignore_rules r
           WHERE r.group_id = m.group_id
             AND r.app_id   = m.app_id
             AND (r.classname_pattern IS NULL
                  OR m.class_name::text ~ r.classname_pattern::text)
             AND (r.name_pattern IS NULL
                  OR m.method_name::text ~ r.name_pattern::text)
       )
    WHERE b.group_id = :group_id
    GROUP BY b.group_id, b.app_id, b.id
),
build_masks AS (
    SELECT
        bm.group_id,
        bm.app_id,
        bm.build_id,
        STRING_AGG(
            CASE WHEN NOT EXISTS (
                SELECT 1
                FROM raw_data.method_ignore_rules r
                WHERE r.group_id = m.group_id
                  AND r.app_id   = m.app_id
                  AND (r.classname_pattern IS NULL
                       OR m.class_name::text ~ r.classname_pattern::text)
                  AND (r.name_pattern IS NULL
                       OR m.method_name::text ~ r.name_pattern::text)
            ) THEN '1'
            ELSE '0' END,
            '' ORDER BY bm.method_id
        )::VARBIT AS method_ignore_mask,
        STRING_AGG(
            CASE WHEN NOT EXISTS (
                SELECT 1
                FROM raw_data.method_ignore_rules r
                WHERE r.group_id = m.group_id
                  AND r.app_id   = m.app_id
                  AND (r.classname_pattern IS NULL
                       OR m.class_name::text ~ r.classname_pattern::text)
                  AND (r.name_pattern IS NULL
                       OR m.method_name::text ~ r.name_pattern::text)
            ) THEN repeat('1', m.probes_count)
            ELSE repeat('0', m.probes_count) END,
            '' ORDER BY bm.method_id
        )::VARBIT AS code_ignore_mask
    FROM raw_data.build_methods bm
    JOIN raw_data.methods m
        ON m.method_id  = bm.method_id
       AND m.app_id     = bm.app_id
       AND m.group_id   = bm.group_id
       AND m.probes_count > 0
    WHERE bm.group_id = :group_id
    GROUP BY bm.group_id, bm.app_id, bm.build_id
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
    bi.validation_status,
    bi.agent_version,
    bi.agent_env,
    bi.agent_params,
    bi.created_at,
    bi.first_instance_created_at,
    bi.last_instance_heartbeat_at,
    bi.updated_at,
    DATE_TRUNC('day', bi.created_at) AS created_at_day,
    DATE_TRUNC('day', bi.updated_at) AS updated_at_day,
    COALESCE(bt.total_classes, 0) AS total_classes,
    COALESCE(bt.total_methods, 0) AS total_methods,
    COALESCE(bt.total_probes,  0) AS total_probes,
    bm2.method_ignore_mask::VARBIT,
    bm2.code_ignore_mask::VARBIT
FROM builds_with_instances bi
LEFT JOIN build_totals bt
    ON bt.group_id = bi.group_id
   AND bt.app_id   = bi.app_id
   AND bt.build_id = bi.id
LEFT JOIN build_masks bm2
    ON bm2.group_id = bi.group_id
   AND bm2.app_id   = bi.app_id
   AND bm2.build_id = bi.id
WHERE bi.updated_at > :since_timestamp
  AND bi.updated_at <= :until_timestamp
ORDER BY bi.updated_at
LIMIT :limit