INSERT INTO metrics.builds_table (
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
    updated_at_day
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
    :updated_at_day
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
    updated_at_day = EXCLUDED.updated_at_day
