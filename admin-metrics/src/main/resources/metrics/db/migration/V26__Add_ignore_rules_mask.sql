ALTER TABLE metrics.builds
    ADD COLUMN IF NOT EXISTS method_ignore_mask VARBIT NULL,
    ADD COLUMN IF NOT EXISTS code_ignore_mask   VARBIT NULL;

ALTER TABLE metrics.methods
    ADD COLUMN IF NOT EXISTS ignore BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS methods_ignore_idx ON metrics.methods (ignore);
