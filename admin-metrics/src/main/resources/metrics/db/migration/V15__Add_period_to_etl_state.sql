-- Add a date period (day granularity, time ignored) to the ETL state tables so that
-- period reruns keep their own watermark and run-lock independently of the incremental
-- run. Existing rows (the incremental lane) get the sentinel bounds representing an
-- unbounded/open range.

ALTER TABLE metrics.etl_metadata
    ADD COLUMN IF NOT EXISTS period_from DATE NOT NULL DEFAULT DATE '0001-01-01',
    ADD COLUMN IF NOT EXISTS period_to   DATE NOT NULL DEFAULT DATE '9999-12-31';

ALTER TABLE metrics.etl_metadata
    DROP CONSTRAINT IF EXISTS etl_metadata_pkey;

ALTER TABLE metrics.etl_metadata
    ADD PRIMARY KEY (
        pipeline_name,
        group_id,
        app_id,
        instance_id,
        build_id,
        test_session_id,
        test_definition_id,
        test_launch_id,
        period_from,
        period_to
    );

ALTER TABLE metrics.etl_runs
    ADD COLUMN IF NOT EXISTS period_from DATE NOT NULL DEFAULT DATE '0001-01-01',
    ADD COLUMN IF NOT EXISTS period_to   DATE NOT NULL DEFAULT DATE '9999-12-31';

ALTER TABLE metrics.etl_runs
    DROP CONSTRAINT IF EXISTS etl_runs_pkey;

ALTER TABLE metrics.etl_runs
    ADD PRIMARY KEY (
        orchestrator_name,
        group_id,
        app_id,
        instance_id,
        build_id,
        test_session_id,
        test_definition_id,
        test_launch_id,
        period_from,
        period_to
    );
