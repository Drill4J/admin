SELECT
    m.group_id,
    m.app_id,
    bm.build_id,
    m.signature,
    m.body_checksum,
    m.probes_count,
    m.method_id,
    m.method_name,
    m.class_name,
    m.method_params,
    m.return_type,
    bm.created_at,
    DATE_TRUNC('day',bm.created_at) AS created_at_day
FROM raw_data.build_methods bm
JOIN raw_data.methods m ON m.method_id = bm.method_id AND m.app_id = bm.app_id AND m.group_id = bm.group_id
WHERE bm.group_id = :group_id
    AND bm.created_at > :since_timestamp
    AND bm.created_at <= :until_timestamp
    AND m.probes_count > 0
    AND NOT EXISTS (
        SELECT 1
        FROM raw_data.method_ignore_rules r
        WHERE r.group_id = m.group_id
            AND r.app_id = m.app_id
            AND (r.classname_pattern IS NOT NULL AND m.class_name::text ~ r.classname_pattern::text
                OR r.name_pattern IS NOT NULL AND m.method_name::text ~ r.name_pattern::text)
    )
ORDER BY bm.created_at ASC, bm.group_id, bm.method_id
LIMIT :limit