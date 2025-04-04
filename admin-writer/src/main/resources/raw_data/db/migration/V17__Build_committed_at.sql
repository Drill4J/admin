ALTER TABLE raw_data.builds
ADD COLUMN committed_at TIMESTAMP WITHOUT TIME ZONE;

UPDATE raw_data.builds
SET committed_at = commit_date::timestamp without time zone
WHERE commit_date IS NOT NULL;

COMMENT ON COLUMN raw_data.builds.commit_date IS 'Deprecated column. Use committed_at instead.';