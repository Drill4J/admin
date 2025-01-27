ALTER TABLE raw_data.test_definitions
ALTER COLUMN tags TYPE varchar[] USING string_to_array(tags, ',');
