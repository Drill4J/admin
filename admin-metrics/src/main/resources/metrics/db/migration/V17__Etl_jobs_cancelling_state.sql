-- Extends the exclusion predicate on etl_jobs to include the transitional CANCELLING status,
-- so a job that has been asked to cancel keeps blocking overlapping schedules until the worker
-- (or the reaper) has actually driven it to CANCELLED. Without this, cancel-then-reschedule
-- races (e.g. rerun) can start a new worker on the same period while the previous one is still
-- winding down.
ALTER TABLE metrics.etl_jobs DROP CONSTRAINT etl_jobs_lock_idx;

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
        WHERE (status IN ('IDLE', 'RUNNING', 'CANCELLING'));
