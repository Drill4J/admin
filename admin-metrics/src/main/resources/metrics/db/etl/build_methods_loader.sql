INSERT INTO metrics.build_methods_table (
    group_id,
    app_id,
    build_id,
    method_id,
    probes_start,
    method_num
)
VALUES (
    :group_id,
    :app_id,
    :build_id,
    :method_id,
    :probes_start,
    :method_num
)
ON CONFLICT (
    group_id,
    app_id,
    build_id,
    method_id
)
DO UPDATE
SET
    probes_start = EXCLUDED.probes_start,
    method_num = EXCLUDED.method_num