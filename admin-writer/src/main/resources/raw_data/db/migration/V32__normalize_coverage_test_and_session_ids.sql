UPDATE raw_data.method_coverage
SET test_session_id = NULL
WHERE test_session_id = 'GLOBAL';

UPDATE raw_data.method_coverage
SET test_id = NULL
WHERE test_id = 'TEST_CONTEXT_NONE';