-- Historical coverage pipelines

UPDATE metrics.etl_metadata
SET pipeline_name = 'historical_test_to_code_mapping'
WHERE pipeline_name = 'test_to_code_mapping'
  AND extractor_name = 'coverage';

-- Incremental coverage pipelines
UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_test_session_coverage'
WHERE pipeline_name = 'build_method_test_session_coverage';

UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_build_method_coverage'
WHERE pipeline_name = 'build_method_coverage'
  AND extractor_name = 'coverage';

UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_method_daily_coverage'
WHERE pipeline_name = 'method_daily_coverage'
  AND extractor_name = 'coverage';

UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_test_session_builds'
WHERE pipeline_name = 'test_session_builds_from_coverage'
  AND extractor_name = 'coverage';

UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_test_definition_coverage_from_test_launches'
WHERE pipeline_name = 'build_method_test_definition_coverage'
  AND extractor_name = 'test_launch_coverage';

UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_test_session_coverage_from_test_launches'
WHERE pipeline_name = 'build_method_test_session_coverage_from_test_launches'
  AND extractor_name = 'test_launch_coverage';

UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_build_method_coverage_from_test_launches'
WHERE pipeline_name = 'build_method_coverage_from_test_launches'
  AND extractor_name = 'test_launch_coverage';

UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_method_daily_coverage_from_test_launches'
WHERE pipeline_name = 'method_daily_coverage_from_test_launches'
  AND extractor_name = 'test_launch_coverage';

UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_test_to_code_mapping_from_test_launches'
WHERE pipeline_name = 'test_to_code_mapping'
  AND extractor_name = 'test_launch_coverage';

UPDATE metrics.etl_metadata
SET pipeline_name = 'incremental_test_session_builds_from_test_launches'
WHERE pipeline_name = 'test_session_builds_from_test_launches'
  AND extractor_name = 'test_launch_coverage';