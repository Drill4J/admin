DROP VIEW IF EXISTS raw_data.view_methods_coverage_v5 CASCADE;
DROP VIEW IF EXISTS raw_data.view_methods_with_rules CASCADE;

-- Rename existing methods table to build_methods
ALTER TABLE raw_data.methods RENAME TO build_methods;

ALTER TABLE raw_data.build_methods
ADD COLUMN IF NOT EXISTS method_id VARCHAR(32);

UPDATE raw_data.build_methods
SET method_id = md5(signature||':'||body_checksum||':'||probes_count)
WHERE method_id IS NULL AND probes_count > 0;

DELETE FROM raw_data.build_methods
WHERE method_id IS NULL;

ALTER TABLE raw_data.build_methods
ALTER COLUMN method_id SET NOT NULL;

CREATE INDEX ON raw_data.build_methods (method_id, app_id, group_id);

-- Create new methods table
CREATE TABLE IF NOT EXISTS raw_data.methods (
    group_id VARCHAR,
    app_id VARCHAR,
    method_id VARCHAR(32),
    signature VARCHAR,
    class_name VARCHAR,
    method_name VARCHAR,
    method_params VARCHAR,
    return_type VARCHAR,
    body_checksum VARCHAR,
    probes_count INT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (method_id, app_id, group_id)
);

-- Migrate data from build_methods to methods
INSERT INTO raw_data.methods (
    group_id,
    app_id,
    method_id,
    signature,
    class_name,
    method_name,
    method_params,
    return_type,
    body_checksum,
    probes_count,
    created_at,
    updated_at
)
SELECT
    group_id,
    app_id,
    method_id,
    MIN(signature) AS signature,
    MIN(classname) AS class_name,
    MIN(name) AS method_name,
    MIN(params) AS method_params,
    MIN(return_type) AS return_type,
    MIN(body_checksum) AS body_checksum,
    MIN(probes_count) AS probes_count,
    MIN(created_at) AS created_at,
    MIN(created_at) AS updated_at
FROM raw_data.build_methods
GROUP BY group_id, app_id, method_id;

CREATE INDEX ON raw_data.methods (signature, app_id, group_id);
CREATE INDEX ON raw_data.methods (method_name, app_id, group_id);
CREATE INDEX ON raw_data.methods (class_name, app_id, group_id);


-- Drop redundant columns from build_methods
ALTER TABLE raw_data.build_methods
DROP CONSTRAINT methods_pkey;

ALTER TABLE raw_data.build_methods
ADD CONSTRAINT build_methods_pkey
PRIMARY KEY (group_id, app_id, build_id, method_id);

ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS id;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS name;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS classname;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS params;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS return_type;

-- Add method_id to method_coverage
ALTER TABLE raw_data.method_coverage ADD COLUMN IF NOT EXISTS method_id VARCHAR(32);

UPDATE raw_data.method_coverage c
SET method_id = md5(c.signature||':'||m.body_checksum||':'||c.probes_count)
FROM raw_data.build_methods bm
JOIN raw_data.methods m ON m.method_id = bm.method_id AND m.app_id = bm.app_id AND m.group_id = bm.group_id
WHERE c.signature = m.signature
  AND c.build_id = bm.build_id
  AND c.app_id = bm.app_id
  AND c.group_id = bm.group_id
  AND c.method_id IS NULL;

CREATE INDEX ON raw_data.method_coverage (method_id, app_id, group_id);