INSERT INTO metrics.builds (
    group_id,
    app_id,
    build_id,
    version_id,
    app_env_ids,
    build_version,
    branch,
    commit_sha,
    commit_author,
    commit_message,
    committed_at,
    created_at,
    updated_at,
    created_at_day,
    updated_at_day,
    first_instance_created_at,
    last_instance_heartbeat_at
)
VALUES (
    :group_id,
    :app_id,
    :build_id,
    :version_id,
    :app_env_ids,
    :build_version,
    :branch,
    :commit_sha,
    :commit_author,
    :commit_message,
    :committed_at,
    :created_at,
    :updated_at,
    :created_at_day,
    :updated_at_day,
    :first_instance_created_at,
    :last_instance_heartbeat_at
)
ON CONFLICT (
    group_id,
    app_id,
    build_id
)
DO UPDATE
SET
    app_env_ids = EXCLUDED.app_env_ids,
    build_version = EXCLUDED.build_version,
    branch = EXCLUDED.branch,
    commit_sha = EXCLUDED.commit_sha,
    commit_author = EXCLUDED.commit_author,
    commit_message = EXCLUDED.commit_message,
    committed_at = EXCLUDED.committed_at,
    updated_at = EXCLUDED.updated_at,
    updated_at_day = EXCLUDED.updated_at_day,
    first_instance_created_at = EXCLUDED.first_instance_created_at,
    last_instance_heartbeat_at = EXCLUDED.last_instance_heartbeat_at
