ALTER TABLE metrics.builds
    ADD COLUMN IF NOT EXISTS total_classes INT NULL,
    ADD COLUMN IF NOT EXISTS total_methods INT NULL,
    ADD COLUMN IF NOT EXISTS total_probes  INT NULL;
