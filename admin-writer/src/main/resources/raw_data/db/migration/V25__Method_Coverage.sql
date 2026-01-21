DROP TABLE raw_data.method_coverage CASCADE;
CREATE TABLE IF NOT EXISTS raw_data.method_coverage(
    id SERIAL PRIMARY KEY,
    instance_id VARCHAR,
    signature VARCHAR,
    test_id VARCHAR,
    probes VARBIT,
    probes_count INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    group_id VARCHAR NOT NULL,
    app_id VARCHAR NOT NULL,
    test_session_id VARCHAR
);

CREATE INDEX IF NOT EXISTS idx_coverage_group_id_created_at ON raw_data.method_coverage(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_coverage_instance_id ON raw_data.method_coverage(group_id, app_id, instance_id, signature, test_session_id, test_id);
CREATE INDEX IF NOT EXISTS idx_coverage_test_id ON raw_data.method_coverage(test_id);

-- extract coverage from original raw_data.coverage table
-- insert into raw_data.method_coverage with probes split per-method
WITH extracted AS (
    SELECT
        c.instance_id,
        m.signature,
        c.test_id,
        SUBSTRING(c.probes FROM m.probe_start_pos + 1 FOR m.probes_count)::varbit AS probes,
        c.group_id,
        c.app_id,
        c.test_session_id,
		c.created_at
    FROM raw_data.coverage c
    JOIN raw_data.instances i
        ON c.instance_id = i.id
    JOIN raw_data.methods m
        ON m.classname = c.classname
        AND m.group_id = c.group_id
        AND m.app_id = c.app_id
        AND m.build_id = i.build_id
        AND BIT_LENGTH(SUBSTRING(c.probes FROM m.probe_start_pos + 1 FOR m.probes_count)::varbit) = m.probes_count
        AND BIT_COUNT(SUBSTRING(c.probes FROM m.probe_start_pos + 1 FOR m.probes_count)::varbit) > 0
)
INSERT INTO raw_data.method_coverage (
    instance_id,
    signature,
    test_id,
    probes,
	probes_count,
    group_id,
    app_id,
    test_session_id,
	created_at
)
SELECT
    e.instance_id,
    e.signature,
    e.test_id,
    e.probes,
    BIT_LENGTH(e.probes) AS probes_count,
    e.group_id,
    e.app_id,
    e.test_session_id,
    e.created_at
FROM extracted e;
