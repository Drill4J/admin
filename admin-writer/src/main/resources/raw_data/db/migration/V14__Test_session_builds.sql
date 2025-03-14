CREATE TABLE IF NOT EXISTS raw_data.test_session_builds (
    test_session_id VARCHAR NOT NULL,
    build_id VARCHAR NOT NULL,
    group_id VARCHAR NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (test_session_id, build_id)
);