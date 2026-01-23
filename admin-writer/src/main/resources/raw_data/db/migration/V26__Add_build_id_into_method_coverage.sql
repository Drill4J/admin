ALTER TABLE raw_data.method_coverage ADD COLUMN build_id VARCHAR;

UPDATE raw_data.method_coverage SET build_id = (
    SELECT build_id
    FROM raw_data.instances
    WHERE group_id = raw_data.method_coverage.group_id
        AND app_id = raw_data.method_coverage.app_id
        AND id = raw_data.method_coverage.instance_id
);