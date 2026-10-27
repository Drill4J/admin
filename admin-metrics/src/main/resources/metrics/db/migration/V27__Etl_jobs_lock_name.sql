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
    WHERE status IN ('IDLE', 'RUNNING', 'CANCELLING')
        AND etl_name = 'incremental';

-- Mirrors EtlContext.conflictsWith: an empty field matches anything, otherwise values must be equal.
-- An empty value is an unbounded range, a non-empty value is a single point; ranges overlap iff the fields conflict.
CREATE TYPE metrics.etl_scope_range AS RANGE (SUBTYPE = text, COLLATION = "C");

CREATE FUNCTION metrics.etl_job_field_scope(field_value text)
    RETURNS metrics.etl_scope_range
    LANGUAGE sql IMMUTABLE AS
$$
SELECT CASE
           WHEN field_value = '' THEN metrics.etl_scope_range(NULL, NULL)
           ELSE metrics.etl_scope_range(field_value, field_value, '[]')
           END
$$;

-- Keep one row of each conflicting group and cancel the others so the constraint below can be created.
UPDATE metrics.etl_jobs r
SET status          = 'CANCELLED',
    finished_at     = CURRENT_TIMESTAMP,
    lock_expires_at = NULL,
    updated_at      = CURRENT_TIMESTAMP
WHERE r.status IN ('IDLE', 'RUNNING', 'CANCELLING')
  AND r.group_id <> ''
  AND EXISTS (SELECT 1
              FROM metrics.etl_jobs o
              WHERE o.ctid < r.ctid
                AND o.status IN ('IDLE', 'RUNNING', 'CANCELLING')
                AND o.group_id = r.group_id
                AND o.lock_name = r.lock_name
                AND o.period && r.period
                AND metrics.etl_job_field_scope(o.app_id) && metrics.etl_job_field_scope(r.app_id)
                AND metrics.etl_job_field_scope(o.build_id) && metrics.etl_job_field_scope(r.build_id)
                AND metrics.etl_job_field_scope(o.test_session_id) && metrics.etl_job_field_scope(r.test_session_id));

ALTER TABLE metrics.etl_jobs
    ADD CONSTRAINT etl_jobs_lock_scope_idx
        EXCLUDE USING gist (
        lock_name WITH =,
        group_id WITH =,
        metrics.etl_job_field_scope(app_id) WITH &&,
        metrics.etl_job_field_scope(build_id) WITH &&,
        metrics.etl_job_field_scope(test_session_id) WITH &&,
        period WITH &&
        )
        WHERE (status IN ('IDLE', 'RUNNING', 'CANCELLING')
            AND group_id <> '');

-- The old lookup index was dropped together with test_definition_id.
CREATE INDEX IF NOT EXISTS etl_jobs_lookup_idx
    ON metrics.etl_jobs (etl_name, group_id, app_id, build_id, test_session_id);

CREATE INDEX IF NOT EXISTS etl_jobs_lock_lookup_idx
    ON metrics.etl_jobs (lock_name, group_id);


ALTER TABLE metrics.etl_metadata
    DROP CONSTRAINT IF EXISTS etl_metadata_pkey;

ALTER TABLE metrics.etl_metadata
    DROP COLUMN IF EXISTS instance_id,
    DROP COLUMN IF EXISTS test_definition_id,
    DROP COLUMN IF EXISTS test_launch_id;

-- Dropping the columns may leave duplicates by the new key; keep the most recently updated row.
DELETE FROM metrics.etl_metadata
WHERE ctid IN (SELECT ctid
               FROM (SELECT ctid,
                            row_number() OVER (
                                PARTITION BY pipeline_name, group_id, app_id, build_id, test_session_id,
                                    period_from, period_to
                                ORDER BY updated_at DESC NULLS LAST, ctid DESC) AS rn
                     FROM metrics.etl_metadata) t
               WHERE rn > 1);

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
