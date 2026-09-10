ALTER TABLE raw_data.test_sessions
ADD COLUMN test_project_id VARCHAR NULL;

ALTER TABLE raw_data.test_definitions
ADD COLUMN test_project_id VARCHAR NULL;
