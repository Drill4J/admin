SELECT
    m.group_id,
    m.app_id,
    m.build_id,
    md5(m.signature||':'||m.body_checksum||':'||m.probes_count) as method_id,
    MIN(m.probes_start) AS probes_start,
    MIN(m.method_num) AS method_num,
    MAX(m.created_at) AS created_at
FROM raw_data.view_methods_with_rules m
JOIN raw_data.builds b ON b.group_id = m.group_id AND b.app_id = m.app_id AND b.id = m.build_id
WHERE m.created_at > ?
    AND m.created_at <= ?
GROUP BY m.group_id, m.app_id, m.build_id, m.signature, m.body_checksum, m.probes_count
ORDER BY created_at ASC, m.build_id ASC, m.signature ASC