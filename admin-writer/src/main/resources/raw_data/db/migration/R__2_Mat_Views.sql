-----------------------------------------------------------------
-- Delete all materialized views
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_methods_coverage;
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_methods_coverage_v2;
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_test_sessions;
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_recommended_tests;

-----------------------------------------------------------------
--Deprecated, use matview_methods_coverage_v2
-----------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_methods_coverage AS
    SELECT
        MIN(group_id) AS group_id,
        MIN(app_id) AS app_id,
        signature,
        body_checksum,
        probes_count,
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
	GROUP BY signature, body_checksum, probes_count, build_id, env_id, test_definition_id, test_result, test_task_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_methods_coverage_pk ON raw_data.matview_methods_coverage (signature, body_checksum, probes_count, build_id, env_id, test_definition_id, test_result, test_task_id);

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_methods_coverage_v2 AS
    SELECT
        MIN(group_id) AS group_id,
        MIN(app_id) AS app_id,
		build_id,
		env_id,
		MIN(branch) AS branch,
		test_tags,
        signature,
        body_checksum,
        probes_count,
        BIT_OR(probes) AS probes,
        BIT_COUNT(BIT_OR(probes)) AS covered_probes,
        MAX(created_at) AS created_at,
        MIN(build_created_at) AS build_created_at
    FROM raw_data.view_methods_coverage_v2
	GROUP BY signature, body_checksum, probes_count, build_id, env_id, test_tags;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_methods_coverage_v2_pk ON raw_data.matview_methods_coverage_v2 (signature, body_checksum, probes_count, build_id, env_id, test_tags);
CREATE INDEX ON raw_data.matview_methods_coverage_v2(group_id, app_id);
CREATE INDEX ON raw_data.matview_methods_coverage_v2(build_id);
CREATE INDEX ON raw_data.matview_methods_coverage_v2(signature, body_checksum, probes_count);

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_test_sessions AS
    SELECT
        test_session_id,
        group_id,
        test_task_id,
        started_at,
        tests,
        result,
        build_id,
        duration,
        failed,
        passed,
        skipped,
        smart_skipped,
        success,
		successful
    FROM raw_data.view_test_sessions;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_test_sessions_pk ON raw_data.matview_test_sessions (test_session_id);
CREATE INDEX ON raw_data.matview_test_sessions(build_id, group_id);

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_recommended_tests AS
    SELECT
        group_id,
        app_id,
        env_id,
        branch,
        test_launch_id,
        MIN(test_task_id) AS test_task_id,
        MIN(test_definition_id) AS test_definition_id,
        MIN(test_tags) AS test_tags,
        signature,
        body_checksum,
        MAX(created_at) AS created_at
    FROM raw_data.view_methods_tests_coverage
	WHERE test_result = 'PASSED'
	GROUP BY group_id, app_id, env_id, branch, test_launch_id, signature, body_checksum;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_recommended_tests_pk ON raw_data.matview_recommended_tests (group_id, app_id, env_id, branch, test_launch_id, signature, body_checksum);
CREATE INDEX ON raw_data.matview_recommended_tests(group_id, app_id);
CREATE INDEX ON raw_data.matview_recommended_tests(signature, body_checksum);