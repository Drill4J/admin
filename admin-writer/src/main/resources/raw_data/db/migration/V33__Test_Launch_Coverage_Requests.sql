CREATE TABLE IF NOT EXISTS raw_data.test_launch_coverage_requests (
    id              SERIAL PRIMARY KEY,
    group_id        VARCHAR(255) NOT NULL,
    test_session_id VARCHAR(255) NOT NULL,
    test_definition_id VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX test_launch_coverage_requests_idx
    ON raw_data.test_launch_coverage_requests (group_id, test_session_id, test_definition_id);