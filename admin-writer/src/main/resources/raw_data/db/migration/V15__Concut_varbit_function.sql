CREATE FUNCTION concat_varbits(state varbit, next_val varbit)
RETURNS varbit AS $$
BEGIN
    IF state IS NULL THEN
        RETURN next_val;
    ELSE
        RETURN state || next_val;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE AGGREGATE concat_varbit (varbit) (
    SFUNC = concat_varbits,
    STYPE = varbit,
    INITCOND = ''
);