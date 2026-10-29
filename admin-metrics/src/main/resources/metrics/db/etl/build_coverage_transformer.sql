SELECT
    bm.method_id,
    COALESCE(SUM(m.probes_count) OVER (
        ORDER BY bm.method_id
        ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
    ), 0) AS probe_start_pos,
    m.probes_count
FROM raw_data.build_methods bm
JOIN raw_data.methods m ON m.method_id = bm.method_id
    AND m.app_id = bm.app_id
    AND m.group_id = bm.group_id
    AND m.probes_count > 0
    AND NOT EXISTS (
        SELECT 1
        FROM raw_data.method_ignore_rules r
        WHERE r.group_id = m.group_id
          AND r.app_id = m.app_id
          AND (r.classname_pattern IS NULL OR m.class_name::text ~ r.classname_pattern::text)
          AND (r.name_pattern IS NULL OR m.method_name::text ~ r.name_pattern::text)
    )
JOIN raw_data.builds b ON b.group_id = bm.group_id
    AND b.app_id = bm.app_id
    AND b.id = bm.build_id
WHERE bm.group_id = :group_id
    AND bm.app_id = :app_id
    AND bm.build_id = :build_id
    AND b.validation_status = 'VALID'
ORDER BY bm.method_id