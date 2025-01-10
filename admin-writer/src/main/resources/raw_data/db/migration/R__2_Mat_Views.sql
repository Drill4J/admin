-----------------------------------------------------------------
-- Delete all materialized views
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_methods_coverage;
-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_methods_coverage AS
    SELECT
        MIN(group_id) AS group_id,
        MIN(app_id) AS app_id,
        signature,
        MIN(body_checksum) AS body_checksum,
        MAX(probes_count) AS probes_count,
        build_id,
        BIT_OR(probes) AS probes,
        BIT_COUNT(BIT_OR(probes)) AS covered_probes,
        MIN(created_at) AS created_at,
        MIN(branch) AS branch,
        env_id,
		test_definition_id,
        test_result,
        test_task_id
    FROM raw_data.view_methods_coverage
	GROUP BY signature, build_id, env_id, test_definition_id, test_result, test_task_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_methods_coverage_pk ON raw_data.matview_methods_coverage (signature, build_id, env_id, test_definition_id, test_result, test_task_id);
