CREATE OR REPLACE FUNCTION raw_data.format_duration(milliseconds bigint)
RETURNS text AS $$
DECLARE
    days integer;
    hours integer;
    minutes integer;
    seconds integer;
    ms integer;
    remainder bigint;
    days_part text;
    hours_part text;
    minutes_part text;
    seconds_part text;
    ms_part text;
BEGIN
    days := milliseconds / 86400000;
    remainder := milliseconds % 86400000;

    hours := remainder / 3600000;
    remainder := remainder % 3600000;

    minutes := remainder / 60000;
    remainder := remainder % 60000;

    seconds := remainder / 1000;
    ms := remainder % 1000;

    days_part := CASE WHEN days > 0 THEN days::text || 'd' END;
    hours_part := CASE WHEN hours > 0 THEN hours::text || 'h' END;
    minutes_part := CASE WHEN minutes > 0 THEN minutes::text || 'm' END;
    seconds_part := CASE WHEN seconds > 0 THEN seconds::text || 's' END;
    ms_part := CASE WHEN ms > 0 THEN ms::text || 'ms' END;

    IF days = 0 AND hours = 0 AND minutes = 0 AND seconds = 0 AND ms = 0 THEN
        RETURN '0ms';
    ELSE
        RETURN CONCAT_WS(' ', days_part, hours_part, minutes_part, seconds_part, ms_part);
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION raw_data.format_duration_rounded(milliseconds bigint)
RETURNS text AS $$
DECLARE
    units text[] := ARRAY['d', 'h', 'm', 's', 'ms'];
    divisors bigint[] := ARRAY[86400000, 3600000, 60000, 1000, 1];
    value integer;
BEGIN
    FOR i IN 1..5 LOOP
        value := milliseconds / divisors[i];
        IF value > 0 THEN
            RETURN value || units[i];
        END IF;
    END LOOP;
    RETURN '0ms';
END;
$$ LANGUAGE plpgsql IMMUTABLE;
