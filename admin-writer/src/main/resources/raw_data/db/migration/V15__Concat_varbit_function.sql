CREATE OR REPLACE FUNCTION concat_varbits(accum varbit, next_val varbit, start_pos integer)
RETURNS varbit AS $$
BEGIN
    IF accum IS NULL THEN
        accum := ''::varbit;
    END IF;

	IF (start_pos > BIT_LENGTH(accum) + 1) THEN
		accum := accum || REPEAT('0', (start_pos - BIT_LENGTH(accum) - 1)::INT)::VARBIT;
	END IF;

    RETURN accum || next_val;
END;
$$ LANGUAGE plpgsql;


CREATE AGGREGATE concat_varbit(varbit, integer)
(
    sfunc = concut_varbits,
    stype = varbit,
    initcond = ''
);