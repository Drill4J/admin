CREATE OR REPLACE VIEW raw_data.view_methods_with_rules AS
    SELECT signature,
        name,
        classname,
        params,
        return_type,
        body_checksum,
        probes_count,
        build_id,
        group_id,
        app_id,
        probe_start_pos,
        NULL::BIGINT AS probes_start,--deprecated
        NULL::BIGINT AS method_num --deprecated
    FROM raw_data.methods m
    WHERE probes_count > 0
        AND NOT EXISTS (
            SELECT 1
            FROM raw_data.method_ignore_rules r
            WHERE r.group_id = m.group_id
		        AND r.app_id = m.app_id
		        AND (r.classname_pattern IS NOT NULL AND m.classname::text ~ r.classname_pattern::text
		            OR r.name_pattern IS NOT NULL AND m.name::text ~ r.name_pattern::text
		            OR r.annotations_pattern IS NOT NULL AND m.annotations::text ~ r.annotations_pattern::text
		            OR r.class_annotations_pattern IS NOT NULL AND m.class_annotations::text ~ r.class_annotations_pattern::text));

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_methods_coverage_v3 AS
SELECT
    c.group_id,
    c.app_id,
    i.build_id,
    MD5(m.signature||':'||m.body_checksum||':'||m.probes_count) AS method_id,
    CASE WHEN c.test_session_id = 'GLOBAL' THEN NULL ELSE c.test_session_id END AS test_session_id,
    CASE WHEN c.test_id = 'TEST_CONTEXT_NONE' THEN NULL ELSE test_id END AS test_launch_id,
    i.env_id AS app_env_id,
    c.created_at,
    SUBSTRING(c.probes FROM m.probe_start_pos + 1 FOR m.probes_count) AS probes
FROM raw_data.coverage c
JOIN raw_data.instances i ON i.group_id = c.group_id AND i.app_id = c.app_id AND i.id = c.instance_id
JOIN raw_data.view_methods_with_rules m ON m.probes_count > 0
    AND m.group_id = i.group_id AND m.app_id = i.app_id AND m.build_id = i.build_id
    AND m.classname = c.classname
    AND BIT_COUNT(SUBSTRING(c.probes FROM m.probe_start_pos + 1 FOR m.probes_count)) > 0
    AND BIT_LENGTH(SUBSTRING(c.probes FROM m.probe_start_pos + 1 FOR m.probes_count)) = m.probes_count;