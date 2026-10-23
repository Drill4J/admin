ALTER TABLE metrics.builds
    ALTER COLUMN first_instance_created_at
        TYPE TIMESTAMP WITHOUT TIME ZONE
        USING first_instance_created_at AT TIME ZONE 'UTC';

ALTER TABLE metrics.builds
    ALTER COLUMN last_instance_heartbeat_at
        TYPE TIMESTAMP WITHOUT TIME ZONE
        USING last_instance_heartbeat_at AT TIME ZONE 'UTC';