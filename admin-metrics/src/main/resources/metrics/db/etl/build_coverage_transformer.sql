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
WHERE bm.group_id = :group_id
    AND bm.app_id = :app_id
    AND bm.build_id = :build_id
ORDER BY bm.method_id