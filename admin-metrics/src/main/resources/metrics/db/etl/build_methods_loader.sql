INSERT INTO metrics.build_methods (
    group_id,
    app_id,
    build_id,
    method_id,
    created_at_day
)
VALUES (
    :group_id,
    :app_id,
    :build_id,
    :method_id,
    :created_at_day
)
ON CONFLICT (
    group_id,
    app_id,
    build_id,
    method_id
)
DO NOTHING