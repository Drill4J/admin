ALTER TABLE metrics.test_sessions
    ADD COLUMN session_status VARCHAR;
ALTER TABLE metrics.test_sessions
    ADD COLUMN last_session_heartbeat_at TIMESTAMP WITHOUT TIME ZONE NULL;