INSERT INTO metrics.methods_table (
    group_id,
    app_id,
    method_id,
    signature,
    method_name,
    class_name,
    method_params,
    return_type,
    body_checksum,
    probes_count
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
    :probes_count
)
ON CONFLICT (
    group_id,
    app_id,
    method_id
)
DO NOTHING