-- raw_data.method_coverage
ALTER TABLE raw_data.method_coverage
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE raw_data.method_coverage
SET updated_at = created_at;

ALTER TABLE raw_data.method_coverage
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

DROP INDEX IF EXISTS raw_data.idx_method_coverage_group_created_null_test;
CREATE INDEX IF NOT EXISTS idx_method_coverage_group_id_updated_at
    ON raw_data.method_coverage (group_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_method_coverage_build_id
    ON raw_data.method_coverage (group_id, app_id, build_id);

-- raw_data.builds
UPDATE raw_data.builds b
SET updated_at = i.last_instance_heartbeat_at
FROM (
    SELECT group_id, build_id, MAX(COALESCE(last_heartbeat_at, created_at)) AS last_instance_heartbeat_at
    FROM raw_data.instances
    GROUP BY group_id, build_id
) i
WHERE i.group_id = b.group_id
  AND i.build_id = b.id
  AND (b.updated_at IS NULL OR i.last_instance_heartbeat_at > b.updated_at);

CREATE INDEX IF NOT EXISTS idx_builds_group_id_updated_at
    ON raw_data.builds (group_id, updated_at);

-- raw_data.test_sessions
ALTER TABLE raw_data.test_sessions
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE raw_data.test_sessions
SET updated_at = COALESCE(last_heartbeat_at, created_at);

ALTER TABLE raw_data.test_sessions
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_test_sessions_group_id_updated_at
    ON raw_data.test_sessions (group_id, updated_at);