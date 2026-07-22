-- Build finalization: integrity validation of builds against their methods checksums
ALTER TABLE raw_data.builds
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS methods_count INTEGER,
    ADD COLUMN IF NOT EXISTS methods_checksum VARCHAR(255),
    ADD COLUMN IF NOT EXISTS finalized_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS builds_retry_idx
    ON raw_data.builds (status)
    WHERE status = 'PENDING';
