ALTER TABLE raw_data.coverage
ADD COLUMN test_session_id VARCHAR;

UPDATE raw_data.coverage SET test_session_id = (
    SELECT tl.test_session_id
    FROM raw_data.test_launches tl
    WHERE tl.id = raw_data.coverage.test_id
) WHERE test_session_id IS NULL AND test_id IS NOT NULL;
