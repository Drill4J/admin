-- Methods pipelines
UPDATE metrics.etl_metadata SET pipeline_name = 'build_methods'
WHERE pipeline_name = 'methods' AND extractor_name = 'build_methods' AND loader_name = 'build_methods';
UPDATE metrics.etl_metadata SET pipeline_name = 'methods'
WHERE pipeline_name = 'methods' AND extractor_name = 'build_methods' AND loader_name = 'methods';

-- Coverage pipelines
UPDATE metrics.etl_metadata SET pipeline_name = 'build_method_test_session_coverage'
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'build_method_test_session_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'method_daily_coverage'
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'method_daily_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'build_method_coverage'
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'build_method_coverage';
UPDATE metrics.etl_metadata SET pipeline_name = 'test_session_builds_from_coverage'
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'test_session_builds';
DELETE FROM metrics.etl_metadata
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'test_to_code_mapping';
DELETE FROM metrics.etl_metadata
WHERE pipeline_name = 'coverage' AND extractor_name = 'coverage' AND loader_name = 'build_method_test_definition_coverage';

-- Test launch coverage pipelines
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

-- Remove duplicates from etl_metadata keeping the most recently updated row
DELETE FROM metrics.etl_metadata
WHERE ctid NOT IN (
    SELECT DISTINCT ON (pipeline_name, group_id) ctid
    FROM metrics.etl_metadata
    ORDER BY pipeline_name, group_id, updated_at DESC
);