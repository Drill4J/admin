-- Build finalization: integrity validation of builds
ALTER TABLE raw_data.builds
    ADD COLUMN IF NOT EXISTS validation_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS methods_count INTEGER,
    ADD COLUMN IF NOT EXISTS methods_checksum VARCHAR(255),
    ADD COLUMN IF NOT EXISTS finalized_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS builds_retry_idx
    ON raw_data.builds (validation_status)
    WHERE validation_status = 'PENDING';

UPDATE raw_data.builds SET validation_status = 'VALID' WHERE validation_status IS NULL;