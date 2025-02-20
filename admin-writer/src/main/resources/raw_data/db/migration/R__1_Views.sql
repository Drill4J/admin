-----------------------------------------------------------------

-----------------------------------------------------------------
DROP VIEW raw_data.view_methods_coverage;
CREATE OR REPLACE VIEW raw_data.view_methods_coverage AS
    SELECT
        builds.group_id,
        builds.app_id,
        methods.signature,
        methods.body_checksum,
        methods.build_id,
        SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count) AS probes,
        BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) AS covered_probes,
        coverage.created_at,
        builds.branch,
        instances.env_id,
        launches.result as test_result,
        sessions.test_task_id,
        launches.test_definition_id,
        launches.id as test_launch_id
    FROM raw_data.coverage coverage
    JOIN raw_data.instances instances ON instances.id = coverage.instance_id
    JOIN raw_data.methods methods ON methods.classname = coverage.classname AND methods.build_id = instances.build_id
    JOIN raw_data.builds builds ON builds.id = methods.build_id
    LEFT JOIN raw_data.test_launches launches ON launches.id = coverage.test_id
    LEFT JOIN raw_data.test_sessions sessions ON sessions.id = launches.test_session_id
    WHERE BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) > 0;

-----------------------------------------------------------------

-----------------------------------------------------------------
DROP VIEW raw_data.view_methods_with_rules;
CREATE OR REPLACE VIEW raw_data.view_methods_with_rules AS
    SELECT signature,
        name,
        classname,
        params,
        return_type,
        body_checksum,
        probes_count,
        group_id,
        app_id,
        build_id
    FROM raw_data.methods m
    WHERE probes_count > 0
        AND NOT EXISTS (
            SELECT 1
            FROM raw_data.method_ignore_rules r
            WHERE r.group_id = m.group_id
		        AND r.app_id = m.app_id
		        AND (r.name_pattern IS NOT NULL AND m.name::text ~ r.name_pattern::text
		            OR r.classname_pattern IS NOT NULL AND m.classname::text ~ r.classname_pattern::text
		            OR r.annotations_pattern IS NOT NULL AND m.annotations::text ~ r.annotations_pattern::text
		            OR r.class_annotations_pattern IS NOT NULL AND m.class_annotations::text ~ r.class_annotations_pattern::text));