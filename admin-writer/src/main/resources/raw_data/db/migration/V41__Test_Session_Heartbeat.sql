ALTER TABLE raw_data.test_sessions
    ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP WITHOUT TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS status            VARCHAR                  NULL;