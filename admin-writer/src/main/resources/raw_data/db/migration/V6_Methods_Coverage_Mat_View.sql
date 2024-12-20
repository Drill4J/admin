CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_methods_coverage AS
    SELECT
        MIN(builds.group_id) AS group_id,
        MIN(builds.app_id) AS app_id,
        methods.signature AS signature,
        MIN(methods.body_checksum) AS body_checksum,
        MAX(methods.probes_count) AS probes_count,
        methods.build_id AS build_id,
        --coverage.test_id AS test_id,
        --coverage.instance_id AS instance_id,
        BIT_OR(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) AS probes,
        BIT_COUNT(BIT_OR(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count))) AS covered_probes,
        MIN(coverage.created_at) AS created_at,
        MIN(builds.branch) AS branch,
        instances.env_id AS env_id,
		launches.test_definition_id AS test_definition_id,
        launches.result AS test_result,
        sessions.test_task_id AS test_task_id
    FROM raw_data.coverage coverage
    JOIN raw_data.instances instances ON instances.id = coverage.instance_id
    JOIN raw_data.methods methods ON methods.classname = coverage.classname AND methods.build_id = instances.build_id
    JOIN raw_data.builds builds ON builds.id = methods.build_id
    LEFT JOIN raw_data.test_launches launches ON launches.id = coverage.test_id
    LEFT JOIN raw_data.test_sessions sessions ON sessions.id = launches.test_session_id
    WHERE BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) > 0
	GROUP BY methods.signature, methods.build_id, instances.env_id, launches.test_definition_id, launches.result, sessions.test_task_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_methods_coverage_pk ON raw_data.matview_methods_coverage (signature, build_id, env_id, test_definition_id, test_result, test_task_id);
