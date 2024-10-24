ALTER TABLE raw_data.methods
ADD COLUMN IF NOT EXISTS annotations VARCHAR;

ALTER TABLE raw_data.method_ignore_rules
ADD COLUMN IF NOT EXISTS annotations_pattern VARCHAR NULL;