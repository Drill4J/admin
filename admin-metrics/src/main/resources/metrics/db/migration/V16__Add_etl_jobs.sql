CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS metrics.etl_jobs
(
    etl_name                   VARCHAR(225) NOT NULL,
    group_id                   VARCHAR(225) NOT NULL DEFAULT '',
    app_id                     VARCHAR(225) NOT NULL DEFAULT '',
    build_id                   VARCHAR(225) NOT NULL DEFAULT '',
    test_session_id            VARCHAR(225) NOT NULL DEFAULT '',
    test_definition_id         VARCHAR(225) NOT NULL DEFAULT '',
    period                     DATERANGE    NOT NULL,
    status                     VARCHAR(50)  NOT NULL,
    error_message              TEXT         NULL,
    processed_until_timestamp  TIMESTAMP    NULL,
    worker_id                  VARCHAR(255) NULL,
    lock_expires_at            TIMESTAMP    NULL,
    started_at                 TIMESTAMP    NULL,
    finished_at                TIMESTAMP    NULL,
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Prevents scheduling/running two jobs for the same orchestrator+context whose day ranges
-- overlap, as long as both are still active (IDLE or RUNNING).
ALTER TABLE metrics.etl_jobs
    ADD CONSTRAINT etl_jobs_lock_idx
        EXCLUDE USING gist (
            etl_name            WITH =,
            group_id            WITH =,
            app_id              WITH =,
            build_id            WITH =,
            test_session_id     WITH =,
            test_definition_id  WITH =,
            period              WITH &&
        )
        WHERE (status IN ('IDLE', 'RUNNING'));

CREATE INDEX IF NOT EXISTS etl_jobs_lookup_idx
    ON metrics.etl_jobs (etl_name, group_id, app_id, build_id, test_session_id, test_definition_id);

-- etl_jobs fully replaces etl_runs: run-locking and progress tracking now live in etl_jobs.
DROP TABLE IF EXISTS metrics.etl_runs;
