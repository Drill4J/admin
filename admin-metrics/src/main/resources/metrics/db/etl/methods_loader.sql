INSERT INTO metrics.methods (
    group_id,
    app_id,
    method_id,
    signature,
    method_name,
    class_name,
    method_params,
    return_type,
    body_checksum,
    probes_count,
    created_at_day,
    ignore
)
VALUES (
    :group_id,
    :app_id,
    :method_id,
    :signature,
    :method_name,
    :class_name,
    :method_params,
    :return_type,
    :body_checksum,
    :probes_count,
    :created_at_day,
    :ignore
)
ON CONFLICT (
    group_id,
    app_id,
    method_id
)
DO UPDATE SET
    ignore = EXCLUDED.ignore;