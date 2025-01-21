-- Add group_id and app_id to "instances"
ALTER TABLE raw_data.instances
ADD COLUMN group_id VARCHAR,
ADD COLUMN app_id VARCHAR;

UPDATE raw_data.instances
SET group_id = builds.group_id,
    app_id = builds.app_id
FROM raw_data.builds
WHERE instances.build_id = builds.id;

DELETE FROM raw_data.instances WHERE group_id IS NULL OR app_id IS NULL;

ALTER TABLE raw_data.instances
ALTER COLUMN group_id SET NOT NULL,
ALTER COLUMN app_id SET NOT NULL;

-- Add group_id and app_id to "coverage"
ALTER TABLE raw_data.coverage
ADD COLUMN group_id VARCHAR,
ADD COLUMN app_id VARCHAR;

UPDATE raw_data.coverage
SET group_id = instances.group_id,
    app_id = instances.app_id
FROM raw_data.instances
WHERE coverage.instance_id = instances.id;

DELETE FROM raw_data.coverage WHERE group_id IS NULL OR app_id IS NULL;

ALTER TABLE raw_data.coverage
ALTER COLUMN group_id SET NOT NULL,
ALTER COLUMN app_id SET NOT NULL;

-- Add group_id, app_id and created_at to "methods"
ALTER TABLE raw_data.methods
ADD COLUMN group_id VARCHAR,
ADD COLUMN app_id VARCHAR,
ADD COLUMN created_at TIMESTAMP;

UPDATE raw_data.methods
SET group_id = builds.group_id,
    app_id = builds.app_id,
    created_at = builds.created_at
FROM raw_data.builds
WHERE methods.build_id = builds.id;

DELETE FROM raw_data.methods WHERE group_id IS NULL OR app_id IS NULL;

ALTER TABLE raw_data.methods
ALTER COLUMN group_id SET NOT NULL,
ALTER COLUMN app_id SET NOT NULL,
ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

