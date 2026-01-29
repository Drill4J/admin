ALTER TABLE raw_data.method_coverage ADD COLUMN IF NOT EXISTS body_checksum VARCHAR;

UPDATE raw_data.method_coverage c
SET body_checksum = m.body_checksum
FROM raw_data.methods m
WHERE c.signature = m.signature
  AND c.build_id = m.build_id
  AND c.app_id = m.app_id
  AND c.group_id = m.group_id
  AND c.body_checksum IS NULL;
