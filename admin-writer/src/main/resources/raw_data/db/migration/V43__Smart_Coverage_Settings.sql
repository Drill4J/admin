ALTER TABLE raw_data.group_settings
    ADD COLUMN smart_coverage_enabled     BOOLEAN  NULL,
    ADD COLUMN smart_coverage_branches    TEXT[]   NULL,
    ADD COLUMN smart_coverage_environments TEXT[]  NULL,
    ADD COLUMN smart_coverage_test_statuses TEXT[] NULL;