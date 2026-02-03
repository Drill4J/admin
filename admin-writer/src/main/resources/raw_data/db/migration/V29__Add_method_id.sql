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