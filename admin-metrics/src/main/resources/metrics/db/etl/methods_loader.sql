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
    probes_count,
    creation_day
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
    :creation_day
)
ON CONFLICT (
    group_id,
    app_id,
    method_id
)
DO NOTHING