ALTER TABLE raw_data.instances
    ADD COLUMN IF NOT EXISTS agent_version VARCHAR NULL,
    ADD COLUMN IF NOT EXISTS agent_env     JSONB   NULL,
    ADD COLUMN IF NOT EXISTS agent_params  JSONB   NULL;

ALTER TABLE raw_data.builds
    ADD COLUMN IF NOT EXISTS agent_version VARCHAR NULL,
    ADD COLUMN IF NOT EXISTS agent_env     JSONB   NULL,
    ADD COLUMN IF NOT EXISTS agent_params  JSONB   NULL;
