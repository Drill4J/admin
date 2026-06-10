ALTER TABLE metrics.etl_metadata
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(225) DEFAULT '',
    ADD COLUMN IF NOT EXISTS build_id VARCHAR(225) DEFAULT '',
    ADD COLUMN IF NOT EXISTS instance_id VARCHAR(225) DEFAULT '',
    ADD COLUMN IF NOT EXISTS test_session_id VARCHAR(225) DEFAULT '',
    ADD COLUMN IF NOT EXISTS test_definition_id VARCHAR(225) DEFAULT '',
    ADD COLUMN IF NOT EXISTS test_launch_id VARCHAR(225) DEFAULT '';

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
        test_launch_id
    );