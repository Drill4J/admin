UPDATE metrics.etl_metadata SET pipeline_name = 'build_method_test_session_coverage'
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'build_method_test_session_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'method_daily_coverage'
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'method_daily_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'build_method_coverage'
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'build_method_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'test_session_builds_from_coverage'
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'test_session_builds';

UPDATE metrics.etl_metadata SET pipeline_name = 'build_method_test_session_coverage_from_test_launches'
WHERE pipeline_name = 'test_launch_coverage' AND extractor_name = 'test_launch_coverage' AND loader_name = 'build_method_test_session_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'build_method_test_definition_coverage'
WHERE pipeline_name = 'test_launch_coverage' AND extractor_name = 'test_launch_coverage' AND loader_name = 'build_method_test_definition_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'method_daily_coverage_from_test_launches'
WHERE pipeline_name = 'test_launch_coverage' AND extractor_name = 'test_launch_coverage' AND loader_name = 'method_daily_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'build_method_coverage_from_test_launches'
WHERE pipeline_name = 'test_launch_coverage' AND extractor_name = 'test_launch_coverage' AND loader_name = 'build_method_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'test_to_code_mapping'
WHERE pipeline_name = 'test_launch_coverage' AND extractor_name = 'test_launch_coverage' AND loader_name = 'test_to_code_mapping';
UPDATE metrics.etl_metadata SET pipeline_name = 'test_session_builds_from_test_launches'
WHERE pipeline_name = 'test_launch_coverage' AND extractor_name = 'test_launch_coverage' AND loader_name = 'test_session_builds';