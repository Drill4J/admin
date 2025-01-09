-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_methods_coverage AS
    SELECT
        builds.group_id,
        builds.app_id,
        methods.signature,
        methods.body_checksum,
        methods.probes_count,
        methods.build_id,
        SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count) AS probes,
        BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) AS covered_probes,
        coverage.created_at,
        builds.branch,
        instances.env_id,
        launches.result as test_result,
        sessions.test_task_id
    FROM raw_data.coverage coverage
    JOIN raw_data.instances instances ON instances.id = coverage.instance_id
    JOIN raw_data.methods methods ON methods.classname = coverage.classname AND methods.build_id = instances.build_id
    JOIN raw_data.builds builds ON builds.id = methods.build_id
    LEFT JOIN raw_data.test_launches launches ON launches.id = coverage.test_id
    LEFT JOIN raw_data.test_sessions sessions ON sessions.id = launches.test_session_id
    WHERE BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) > 0;
