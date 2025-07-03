CREATE OR REPLACE FUNCTION metrics.get_metrics_period(input_group_id VARCHAR)
RETURNS TIMESTAMP AS $$
DECLARE
    period_days INTEGER;
    result TIMESTAMP;
BEGIN
    SELECT gs.metrics_period_days
    INTO period_days
    FROM raw_data.group_settings gs
    WHERE gs.group_id = input_group_id;

    IF period_days IS NULL THEN
        RETURN TIMESTAMP '1900-01-01 00:00:00';
    END IF;

    result := CURRENT_DATE - period_days;

    RETURN result;
END;
$$ LANGUAGE plpgsql;