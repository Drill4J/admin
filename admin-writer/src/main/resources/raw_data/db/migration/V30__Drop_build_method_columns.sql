-- Drop redundant columns from build_methods
ALTER TABLE raw_data.build_methods
DROP CONSTRAINT methods_pkey;

ALTER TABLE raw_data.build_methods
ADD CONSTRAINT build_methods_pkey
PRIMARY KEY (group_id, app_id, build_id, method_id);

ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS id CASCADE;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS name CASCADE;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS classname CASCADE;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS params CASCADE;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS return_type CASCADE;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS signature CASCADE;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS probes_count CASCADE;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS body_checksum CASCADE;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS annotations CASCADE;
ALTER TABLE raw_data.build_methods
DROP COLUMN IF EXISTS class_annotations CASCADE;

-- Drop redundant columns from method_coverage
ALTER TABLE raw_data.method_coverage
DROP COLUMN IF EXISTS signature CASCADE;