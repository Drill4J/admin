ALTER TABLE raw_data.instances
ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP WITHOUT TIME ZONE NULL;

ALTER TABLE raw_data.instances
ADD COLUMN IF NOT EXISTS status VARCHAR NULL;

COMMENT ON COLUMN raw_data.instances.last_heartbeat_at IS 'Timestamp of the last heartbeat received from the Agent.';
COMMENT ON COLUMN raw_data.instances.status IS 'Last status value reported by the Agent (RUNNING or SHUTDOWN).';
