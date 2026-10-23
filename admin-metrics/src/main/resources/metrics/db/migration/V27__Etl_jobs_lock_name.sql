ALTER TABLE metrics.etl_jobs
    ADD COLUMN IF NOT EXISTS lock_name VARCHAR(225);

UPDATE metrics.etl_jobs
SET lock_name = CASE WHEN etl_name = 'testSessionCoverage' THEN 'test_session_coverage' ELSE 'metrics' END
WHERE lock_name IS NULL;

ALTER TABLE metrics.etl_jobs
    ALTER COLUMN lock_name SET NOT NULL;

ALTER TABLE metrics.etl_jobs
    DROP CONSTRAINT etl_jobs_lock_idx;

ALTER TABLE metrics.etl_jobs
    DROP COLUMN IF EXISTS test_definition_id;

UPDATE metrics.etl_jobs
    SET status = 'CANCELLED'
    WHERE status IN ('IDLE', 'RUNNING')
        AND etl_name = 'incremental';

ALTER TABLE metrics.etl_jobs
    ADD CONSTRAINT etl_jobs_lock_groups_idx
        EXCLUDE USING gist (
        lock_name WITH =,
        group_id WITH =,
        period WITH &&
        )
        WHERE (status IN ('IDLE', 'RUNNING', 'CANCELLING')
            AND group_id <> ''
            AND app_id = ''
            AND build_id = ''
            AND test_session_id = '');

ALTER TABLE metrics.etl_jobs
    ADD CONSTRAINT etl_jobs_lock_apps_idx
        EXCLUDE USING gist (
        lock_name WITH =,
        group_id WITH =,
        app_id WITH =,
        period WITH &&
        )
        WHERE (status IN ('IDLE', 'RUNNING', 'CANCELLING')
            AND group_id <> ''
            AND app_id <> ''
            AND build_id = ''
            AND test_session_id = '');

ALTER TABLE metrics.etl_jobs
    ADD CONSTRAINT etl_jobs_lock_builds_idx
        EXCLUDE USING gist (
        lock_name WITH =,
        group_id WITH =,
        app_id WITH =,
        build_id WITH =,
        period WITH &&
        )
        WHERE (status IN ('IDLE', 'RUNNING', 'CANCELLING')
            AND group_id <> ''
            AND app_id <> ''
            AND build_id <> ''
            AND test_session_id = '');

ALTER TABLE metrics.etl_jobs
    ADD CONSTRAINT etl_jobs_lock_test_sessions_idx
        EXCLUDE USING gist (
        lock_name WITH =,
        group_id WITH =,
        test_session_id WITH =,
        period WITH &&
        )
        WHERE (status IN ('IDLE', 'RUNNING', 'CANCELLING')
            AND group_id <> ''
            AND test_session_id <> ''
            AND app_id = ''
            AND build_id = '');

CREATE INDEX IF NOT EXISTS etl_jobs_lock_lookup_idx
    ON metrics.etl_jobs (lock_name, group_id);


ALTER TABLE metrics.etl_metadata
    DROP CONSTRAINT IF EXISTS etl_metadata_pkey;

ALTER TABLE metrics.etl_metadata
    DROP COLUMN IF EXISTS instance_id,
    DROP COLUMN IF EXISTS test_definition_id,
    DROP COLUMN IF EXISTS test_launch_id;

ALTER TABLE metrics.etl_metadata
    ADD PRIMARY KEY (
                     pipeline_name,
                     group_id,
                     app_id,
                     build_id,
                     test_session_id,
                     period_from,
                     period_to
        );
