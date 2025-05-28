-----------------------------------------------------------------

-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_risks CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_risks(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR,

    methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL
) RETURNS TABLE (
    __risk_type TEXT,
    __build_id VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
    __classname VARCHAR,
    __body_checksum VARCHAR,
    __signature VARCHAR,
    __probe_start_pos INT,
    __probes_count INT
) AS $$
BEGIN
    RETURN QUERY
    WITH
    BaselineMethods AS (
        SELECT * FROM raw_data.get_methods(input_baseline_build_id, methods_class_name_pattern, methods_method_name_pattern)
    ),
    Methods AS (
        SELECT * FROM raw_data.get_methods(input_build_id, methods_class_name_pattern, methods_method_name_pattern)
    ),
    RisksModified AS (
        SELECT
            build_id,
            name,
            params,
            return_type,
            classname,
            body_checksum,
            signature,
            probe_start_pos,
            probes_count
        FROM Methods AS q2
        WHERE
            EXISTS (
                SELECT 1
                FROM BaselineMethods AS q1
                WHERE q1.signature = q2.signature
                AND q1.body_checksum <> q2.body_checksum
            )
    ),
    RisksNew AS (
        SELECT
            build_id,
            name,
            params,
            return_type,
            classname,
            body_checksum,
            signature,
            probe_start_pos,
            probes_count
        FROM Methods AS q2
        WHERE
            NOT EXISTS (
                SELECT 1
                FROM BaselineMethods AS q1
                WHERE q1.signature = q2.signature
            )
    )
    SELECT
		'new' as risk_type,
		*
	FROM RisksNew
    UNION
    SELECT
		 'modified' as risk_type,
		*
	FROM RisksModified
;
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------

-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.check_build_exists CASCADE;

CREATE OR REPLACE FUNCTION raw_data.check_build_exists(
    build_id VARCHAR
) RETURNS BOOLEAN AS $$
DECLARE
    build_exists BOOLEAN;
BEGIN
    SELECT EXISTS(
        SELECT 1
		FROM raw_data.builds builds
        WHERE builds.id = build_id
    ) INTO build_exists;
    RETURN build_exists;
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------
--Deprecated, use get_methods_v2 instead
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_methods CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_methods(
    input_build_id VARCHAR,
    methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    id VARCHAR,
    build_id VARCHAR,
    classname VARCHAR,
    name VARCHAR,
    params VARCHAR,
    return_type VARCHAR,
    body_checksum VARCHAR,
    signature VARCHAR,
    probe_start_pos INT,
    probes_count INT,
    annotations VARCHAR,
    class_annotations VARCHAR,
    group_id VARCHAR,
    app_id VARCHAR,
    created_at TIMESTAMP
)
AS $$
BEGIN
    RETURN QUERY
    SELECT *
    FROM raw_data.methods methods
    WHERE methods.build_id = input_build_id
        AND methods.probes_count > 0
        AND (methods_class_name_pattern IS NULL OR methods.classname LIKE methods_class_name_pattern)
        AND (methods_method_name_pattern IS NULL OR methods.name LIKE methods_method_name_pattern)
        AND NOT EXISTS (
           SELECT 1
           FROM raw_data.method_ignore_rules r
           WHERE ((r.name_pattern IS NOT NULL AND methods.name ~ r.name_pattern)
             OR (r.classname_pattern IS NOT NULL AND methods.classname ~ r.classname_pattern)
             OR (r.annotations_pattern IS NOT NULL AND methods.annotations ~ r.annotations_pattern)
             OR (r.class_annotations_pattern IS NOT NULL AND methods.class_annotations ~ r.class_annotations_pattern))
             AND r.group_id = split_part(input_build_id, ':', 1)
             AND r.app_id = split_part(input_build_id, ':', 2)
        );
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------

-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_instance_ids CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_instance_ids(input_build_id VARCHAR)
RETURNS TABLE (
    __id VARCHAR,
    __build_id VARCHAR,
    __created_at TIMESTAMP,
    __env_id VARCHAR,
    __group_id VARCHAR,
    __app_id VARCHAR
)
AS $$
BEGIN
    RETURN QUERY
    SELECT *
    FROM raw_data.instances
    WHERE build_id = input_build_id;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get coverage aggregated by methods of a given build with filters by test sessions and test launches
-- @param input_build_id VARCHAR: The ID of the build
-- @param input_baseline_build_id VARCHAR DEFAULT NULL: The ID of the baseline build (optional)
-- @param input_aggregated_coverage BOOLEAN DEFAULT FALSE: Flag to indicate if aggregated coverage should be used
-- @param input_test_session_id VARCHAR DEFAULT NULL: The ID of the test session (optional)
-- @param input_test_launch_id VARCHAR DEFAULT NULL: The ID of the test launch (optional)
-- @param input_env_id VARCHAR DEFAULT NULL: The ID of the environment (optional)
-- @param input_coverage_period_from TIMESTAMP DEFAULT NULL: The start of the coverage period (optional)
-- @param input_package_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by package name (optional)
-- @param input_class_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by class name (optional)
-- @param input_method_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by method name (optional)
-- @returns TABLE: A table containing methods tests coverage
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_methods_tests_coverage CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_methods_tests_coverage(
    input_build_id VARCHAR,
    input_baseline_build_id VARCHAR DEFAULT NULL,
    input_aggregated_coverage BOOLEAN DEFAULT FALSE,
    input_test_session_id VARCHAR DEFAULT NULL,
    input_test_launch_id VARCHAR DEFAULT NULL,
    input_env_id VARCHAR DEFAULT NULL,
    input_coverage_period_from TIMESTAMP DEFAULT NULL,
    input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    build_id VARCHAR,
	signature VARCHAR,
    classname VARCHAR,
    name VARCHAR,
    params VARCHAR,
    return_type VARCHAR,
    probes_count INT,
    isolated_covered_probes INT,
    isolated_missed_probes INT,
    isolated_probes_coverage_ratio FLOAT,
   	aggregated_covered_probes INT,
    aggregated_missed_probes INT,
    aggregated_probes_coverage_ratio FLOAT,
    change_type VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    WITH
    BaselineMethods AS (
    		SELECT
    			baseline.signature,
                baseline.body_checksum
    		FROM raw_data.view_methods_with_rules baseline
    		WHERE baseline.build_id = input_baseline_build_id
    ),
    TargetMethods AS (
        SELECT
			target.group_id,
			target.app_id,
			target.build_id,
            target.signature,
            target.body_checksum,
            target.probes_count,
            (CASE WHEN baseline.signature IS NULL THEN 'new' ELSE 'modified' END) AS change_type
        FROM raw_data.view_methods_with_rules target
        LEFT JOIN BaselineMethods baseline ON baseline.signature = target.signature
        WHERE target.build_id = input_build_id
            --filter by package pattern
            AND (input_package_name_pattern IS NULL OR target.classname LIKE input_package_name_pattern)
            --filter by class pattern
            AND (input_class_name_pattern IS NULL OR target.classname LIKE input_class_name_pattern)
            --filter by method pattern
            AND (input_method_name_pattern IS NULL OR target.name LIKE input_method_name_pattern)
            --filter by baseline
            AND (input_baseline_build_id IS NULL OR baseline.signature IS NULL OR baseline.body_checksum <> target.body_checksum)
    ),
    TargetMethodCoverage AS (
        SELECT
            target.build_id,
            target.signature,
            MAX(target.probes_count) AS probes_count,
            BIT_COUNT(BIT_OR(coverage.probes)) AS aggregated_covered_probes,
            BIT_COUNT(BIT_OR(CASE
                WHEN coverage.build_id = target.build_id
                 --isolation coverage by test session
                 AND (input_test_session_id IS NULL OR coverage.test_session_id = input_test_session_id)
                 --isolation coverage by test launch
                 AND (input_test_launch_id IS NULL OR coverage.test_launch_id = input_test_launch_id)
                THEN coverage.probes ELSE null
            END)) AS isolated_covered_probes,
            MIN(target.change_type) AS change_type
        FROM TargetMethods target
        LEFT JOIN raw_data.view_methods_coverage_v2 coverage ON target.signature = coverage.signature
            AND target.body_checksum = coverage.body_checksum
            AND target.probes_count = coverage.probes_count
            AND coverage.test_launch_id IS NOT NULL
            --filter by group
            AND coverage.group_id = target.group_id
            --filter by app
            AND coverage.app_id = split_part(input_build_id, ':', 2)
            --filter by only isolated coverage
            AND (input_aggregated_coverage IS TRUE OR coverage.build_id = target.build_id)
            --filter by test session
            AND (input_aggregated_coverage IS TRUE OR input_test_session_id IS NULL OR coverage.test_session_id = input_test_session_id)
            AND (input_aggregated_coverage IS FALSE OR input_test_session_id IS NULL OR coverage.test_task_id = (SELECT test_task_id FROM raw_data.test_sessions WHERE id = input_test_session_id))
            --filter by test launch
            AND (input_aggregated_coverage IS TRUE OR input_test_launch_id IS NULL OR coverage.test_launch_id = input_test_launch_id)
            AND (input_aggregated_coverage IS FALSE OR input_test_launch_id IS NULL OR coverage.test_definition_id = (SELECT test_definition_id FROM raw_data.test_launches WHERE id = input_test_launch_id))
            --filter by env
            AND (input_env_id IS NULL OR coverage.env_id = input_env_id)
            --filter by coverage period form
            AND (input_coverage_period_from IS NULL OR coverage.created_at >= input_coverage_period_from)
        GROUP BY target.build_id, target.signature
    )
    SELECT
        coverage.build_id::VARCHAR,
    	coverage.signature::VARCHAR,
        methods.classname,
        methods.name,
        methods.params,
        methods.return_type,
        coverage.probes_count::INT,
        COALESCE(coverage.isolated_covered_probes, 0)::INT AS isolated_covered_probes,
        (coverage.probes_count - COALESCE(coverage.isolated_covered_probes, 0))::INT AS isolated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.isolated_covered_probes, 0) AS FLOAT) / coverage.probes_count, 0.0) AS isolated_probes_coverage_ratio,
        COALESCE(coverage.aggregated_covered_probes, 0)::INT AS aggregated_covered_probes,
        (coverage.probes_count - COALESCE(coverage.aggregated_covered_probes, 0))::INT AS aggregated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.aggregated_covered_probes, 0) AS FLOAT) / coverage.probes_count, 0.0) AS aggregated_probes_coverage_ratio,
		coverage.change_type::VARCHAR
    FROM TargetMethodCoverage coverage
    JOIN raw_data.methods methods ON methods.build_id = coverage.build_id AND methods.signature = coverage.signature;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get coverage aggregated by a given build with filters by test sessions and test launches
-- @param input_build_id VARCHAR: The ID of the build
-- @param input_baseline_build_id VARCHAR DEFAULT NULL: The ID of the baseline build (optional)
-- @param input_aggregated_coverage BOOLEAN DEFAULT FALSE: Flag to indicate if aggregated coverage should be used
-- @param input_test_session_id VARCHAR DEFAULT NULL: The ID of the test session (optional)
-- @param input_test_launch_id VARCHAR DEFAULT NULL: The ID of the test launch (optional)
-- @param input_env_id VARCHAR DEFAULT NULL: The ID of the environment (optional)
-- @param input_coverage_period_from TIMESTAMP DEFAULT NULL: The start of the coverage period (optional)
-- @param input_package_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by package name (optional)
-- @param input_class_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by class name (optional)
-- @param input_method_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by method name (optional)
-- @returns TABLE: A table containing build tests coverage
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_build_tests_coverage CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_build_tests_coverage(
    input_build_id VARCHAR,
    input_baseline_build_id VARCHAR DEFAULT NULL,
    input_aggregated_coverage BOOLEAN DEFAULT FALSE,
    input_test_session_id VARCHAR DEFAULT NULL,
    input_test_launch_id VARCHAR DEFAULT NULL,
    input_env_id VARCHAR DEFAULT NULL,
    input_coverage_period_from TIMESTAMP DEFAULT NULL,
    input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL
) RETURNS TABLE(
    build_id VARCHAR,
    total_probes INT,
    isolated_covered_probes INT,
    isolated_missed_probes INT,
    isolated_probes_coverage_ratio FLOAT,
	aggregated_covered_probes INT,
    aggregated_missed_probes INT,
    aggregated_probes_coverage_ratio FLOAT,
    total_changes INT,
    isolated_tested_changes INT,
    isolated_partially_tested_changes INT,
    aggregated_tested_changes INT,
    aggregated_partially_tested_changes INT
) AS $$
BEGIN
    RETURN QUERY
    WITH
	BaselineMethods AS (
        SELECT
            baseline.signature,
            baseline.body_checksum
        FROM raw_data.view_methods_with_rules baseline
        WHERE baseline.build_id = input_baseline_build_id
    ),
    TargetMethods AS (
        SELECT
            target.build_id,
            target.signature,
            target.body_checksum,
            target.probes_count
        FROM raw_data.view_methods_with_rules target
        LEFT JOIN BaselineMethods baseline ON baseline.signature = target.signature
        WHERE target.build_id = input_build_id
            --filter by package pattern
            AND (input_package_name_pattern IS NULL OR target.classname LIKE input_package_name_pattern)
            --filter by class pattern
            AND (input_class_name_pattern IS NULL OR target.classname LIKE input_class_name_pattern)
            --filter by method pattern
            AND (input_method_name_pattern IS NULL OR target.name LIKE input_method_name_pattern)
            --filter by baseline
            AND (input_baseline_build_id IS NULL OR baseline.signature IS NULL OR baseline.body_checksum <> target.body_checksum)
    ),
    TargetMethodCoverage AS (
        SELECT
            target.build_id,
            target.signature,
            MAX(target.probes_count) AS probes_count,
            BIT_COUNT(BIT_OR(coverage.probes)) AS aggregated_covered_probes,
            BIT_COUNT(BIT_OR(CASE
                WHEN coverage.build_id = target.build_id
                 --isolation coverage by test session
                 AND (input_test_session_id IS NULL OR coverage.test_session_id = input_test_session_id)
                 --isolation coverage by test launch
                 AND (input_test_launch_id IS NULL OR coverage.test_launch_id = input_test_launch_id)
                THEN coverage.probes ELSE null
            END)) AS isolated_covered_probes
        FROM TargetMethods target
        LEFT JOIN raw_data.view_methods_tests_coverage coverage ON target.signature = coverage.signature
            AND target.body_checksum = coverage.body_checksum
            AND target.probes_count = coverage.probes_count
            --filter by group
            AND coverage.group_id = split_part(input_build_id, ':', 1)
            --filter by app
            AND coverage.app_id = split_part(input_build_id, ':', 2)
            --filter by only isolated coverage
            AND (input_aggregated_coverage IS TRUE OR coverage.build_id = target.build_id)
            --filter by test session
            AND (input_aggregated_coverage IS TRUE OR input_test_session_id IS NULL OR coverage.test_session_id = input_test_session_id)
            AND (input_aggregated_coverage IS FALSE OR input_test_session_id IS NULL OR coverage.test_task_id = (SELECT test_task_id FROM raw_data.test_sessions WHERE id = input_test_session_id))
            --filter by test launch
            AND (input_aggregated_coverage IS TRUE OR input_test_launch_id IS NULL OR coverage.test_launch_id = input_test_launch_id)
            AND (input_aggregated_coverage IS FALSE OR input_test_launch_id IS NULL OR coverage.test_definition_id = (SELECT test_definition_id FROM raw_data.test_launches WHERE id = input_test_launch_id))
            --filter by env
            AND (input_env_id IS NULL OR coverage.env_id = input_env_id)
            --filter by coverage period form
            AND (input_coverage_period_from IS NULL OR coverage.created_at >= input_coverage_period_from)
        GROUP BY target.build_id, target.signature
    ),
    CoverageGroupedByBuilds AS (
        SELECT
            coverage.build_id,
			COUNT(*) AS total_changes,
            SUM(coverage.probes_count) AS total_probes,
            SUM(coverage.aggregated_covered_probes) AS aggregated_covered_probes,
            SUM(coverage.isolated_covered_probes) AS isolated_covered_probes,
			SUM(
				CASE WHEN coverage.isolated_covered_probes = coverage.probes_count THEN 1 ELSE 0 END
			) AS isolated_tested_changes,
			SUM(
				CASE WHEN coverage.isolated_covered_probes > 0 AND coverage.isolated_covered_probes < coverage.probes_count THEN 1 ELSE 0 END
			) AS isolated_partially_tested_changes,
			SUM(
				CASE WHEN coverage.aggregated_covered_probes = coverage.probes_count THEN 1 ELSE 0 END
			) AS aggregated_tested_changes,
			SUM(
				CASE WHEN coverage.aggregated_covered_probes > 0 AND coverage.aggregated_covered_probes < coverage.probes_count THEN 1 ELSE 0 END
			) AS aggregated_partially_tested_changes
        FROM TargetMethodCoverage coverage
        GROUP BY coverage.build_id
    )
    SELECT
        coverage.build_id::VARCHAR,
        coverage.total_probes::INT,
        COALESCE(coverage.isolated_covered_probes, 0)::INT AS isolated_covered_probes,
        (coverage.total_probes - COALESCE(coverage.isolated_covered_probes, 0))::INT AS isolated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.isolated_covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) AS isolated_probes_coverage_ratio,
        COALESCE(coverage.aggregated_covered_probes, 0)::INT AS aggregated_covered_probes,
        (coverage.total_probes - COALESCE(coverage.aggregated_covered_probes, 0))::INT AS aggregated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.aggregated_covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) AS aggregated_probes_coverage_ratio,
        coverage.total_changes::INT,
		COALESCE(coverage.isolated_tested_changes, 0)::INT AS isolated_tested_changes,
		COALESCE(coverage.isolated_partially_tested_changes, 0)::INT AS isolated_partially_tested_changes,
		COALESCE(coverage.aggregated_tested_changes, 0)::INT AS aggregated_tested_changes,
		COALESCE(coverage.aggregated_partially_tested_changes, 0)::INT AS aggregated_partially_tested_changes
    FROM CoverageGroupedByBuilds coverage;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get recommended tests for a given build
-- @param input_target_build_id VARCHAR: The ID of the build to be tested
-- @param input_tests_to_skip BOOLEAN DEFAULT FALSE: A flag indicating that the function will return tests that should be skipped
-- @param input_test_task_id VARCHAR DEFAULT NULL: The ID of the test task that will be launched for testing (optional)
-- @param input_test_tag VARCHAR DEFAULT NULL: Coverage collected from tests marked with this test tag (optional)
-- @param input_baseline_build_id VARCHAR DEFAULT NULL: The ID of the baseline build (optional)
-- @param input_coverage_period_from TIMESTAMP DEFAULT NULL: Date from which to take into account the coverage (optional)
-- @param input_env_id VARCHAR DEFAULT NULL: Coverage collected from instances marked with this Environment ID (optional)
-- @param input_branch VARCHAR DEFAULT NULL: Coverage collected from builds of this branch (optional)
-- @param input_chronological BOOLEAN DEFAULT TRUE: Flag to indicate if coverage should only be obtained in builds created earlier than the current one
-- @param input_materialized BOOLEAN DEFAULT TRUE: Flag to indicate if materialized views should be used
-- @returns TABLE: A table containing recommended tests
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_recommended_tests_v4 CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_recommended_tests_v4(
    input_target_build_id VARCHAR,
	input_tests_to_skip BOOLEAN DEFAULT FALSE,
	input_test_task_id VARCHAR DEFAULT NULL,
	input_test_tag VARCHAR DEFAULT NULL,
	input_baseline_build_id VARCHAR DEFAULT NULL,
	input_coverage_period_from TIMESTAMP DEFAULT NULL,
	input_branch VARCHAR DEFAULT NULL,
	input_env_id VARCHAR DEFAULT NULL,
	input_chronological BOOLEAN DEFAULT TRUE,
	input_materialized BOOLEAN DEFAULT TRUE
) RETURNS TABLE(
    test_definition_id VARCHAR,
    path VARCHAR,
    name VARCHAR,
    runner VARCHAR,
    tags VARCHAR[],
    metadata JSON
) AS $$
DECLARE
    use_materialized BOOLEAN;
BEGIN
	use_materialized := input_materialized
        AND input_baseline_build_id IS NULL
		AND input_chronological IS TRUE;

    RETURN QUERY
    WITH
    BaselineMethods AS (
        SELECT
            baseline.signature,
            baseline.body_checksum
        FROM raw_data.view_methods_with_rules baseline
        WHERE baseline.build_id = input_baseline_build_id
    ),
    TargetMethods AS (
        SELECT
            target.build_id,
            target.signature,
            target.body_checksum,
            target.probes_count,
            (CASE WHEN baseline.signature IS NULL THEN 'new' ELSE 'modified' END) AS change_type,
            builds.created_at AS build_created_at
        FROM raw_data.view_methods_with_rules target
        JOIN raw_data.builds builds ON builds.id = target.build_id
        LEFT JOIN BaselineMethods baseline ON baseline.signature = target.signature
        WHERE target.build_id = input_target_build_id
            --filter by baseline
            AND (input_baseline_build_id IS NULL OR baseline.signature IS NULL OR baseline.body_checksum <> target.body_checksum)
    ),
    TestedBuildsComparison AS (
        SELECT
            tested.test_definition_id,
            tested.test_launch_id,
            BOOL_OR(target.body_checksum <> tested.body_checksum) AS has_changed_methods
        FROM raw_data.view_tested_methods tested
        JOIN TargetMethods target ON target.signature = tested.signature
        WHERE tested.group_id = split_part(input_target_build_id, ':', 1)
          AND tested.app_id = split_part(input_target_build_id, ':', 2)
          --filter by chronological order
          AND (input_chronological IS FALSE OR tested.build_created_at <= target.build_created_at)
          --filter by test task id
          AND (input_test_task_id IS NULL OR tested.test_task_id = input_test_task_id)
          --filter by coverage period from
          AND (input_coverage_period_from IS NULL OR tested.session_started_at >= input_coverage_period_from)
          --filter by branch
          AND (input_branch IS NULL OR tested.branch = input_branch)
          --filter by env
          AND (input_env_id IS NULL OR tested.env_id = input_env_id)
          --filter by test tag
          AND (input_test_tag IS NULL OR input_test_tag = ANY(tested.test_tags))
        GROUP BY tested.test_definition_id, tested.test_launch_id
    ),
    RecommendedTests AS (
        SELECT
            tests.test_definition_id
        FROM TestedBuildsComparison tests
        GROUP BY tests.test_definition_id
        HAVING BOOL_AND(tests.has_changed_methods) = NOT input_tests_to_skip
    ),
    TestedBuildsComparisonMaterialized AS (
        SELECT
            tl.test_definition_id,
            tests.test_launch_id,
            tests.has_changed_methods
        FROM raw_data.matview_tested_builds_comparison tests
        JOIN raw_data.builds tb ON tb.id = tests.tested_build_id
        JOIN raw_data.test_launches tl ON tl.id = tests.test_launch_id
        JOIN raw_data.test_definitions td ON td.id = tl.test_definition_id
        JOIN raw_data.test_sessions ts ON ts.id = tl.test_session_id
        WHERE tests.target_build_id = input_target_build_id
          --filter by test task id
          AND (input_test_task_id IS NULL OR ts.test_task_id = input_test_task_id)
          --filter by coverage period from
          AND (input_coverage_period_from IS NULL OR ts.started_at >= input_coverage_period_from)
          --filter by branch
          AND (input_branch IS NULL OR tb.branch = input_branch)
          --filter by env
          AND (input_env_id IS NULL OR tests.env_id = input_env_id)
          --filter by test tag
          AND (input_test_tag IS NULL OR input_test_tag = ANY(td.tags))
    ),
    RecommendedTestsMaterialized AS (
        SELECT
            tests.test_definition_id
        FROM TestedBuildsComparisonMaterialized tests
        GROUP BY tests.test_definition_id
        HAVING BOOL_AND(tests.has_changed_methods) = NOT input_tests_to_skip
    )
    SELECT
        tests.id,
        tests.path,
        tests.name,
        tests.runner,
        tests.tags,
        tests.metadata
    FROM raw_data.test_definitions tests
    WHERE tests.group_id = split_part(input_target_build_id, ':', 1)
      AND (
	  	  --filter by materialized view
		  (use_materialized AND EXISTS (SELECT 1 FROM RecommendedTestsMaterialized rtests WHERE rtests.test_definition_id = tests.id))
	      OR
	      --filter by non materialized view
	      (NOT use_materialized AND EXISTS (SELECT 1 FROM RecommendedTests rtests WHERE rtests.test_definition_id = tests.id))
	  )
	 ;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Deprecated, use get_build_coverage_trends_v3 instead
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_build_coverage_trends_v2 CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_build_coverage_trends_v2(
    input_group_id VARCHAR,
    input_app_id VARCHAR,
	input_baseline_build_id VARCHAR DEFAULT NULL,
	input_aggregated_coverage BOOLEAN DEFAULT FALSE,
	input_test_tag VARCHAR DEFAULT NULL,
	input_env_id VARCHAR DEFAULT NULL,
	input_branch VARCHAR DEFAULT NULL,
	input_coverage_period_from TIMESTAMP DEFAULT NULL,
	input_builds_limit INT DEFAULT 10,
	input_chronological BOOLEAN DEFAULT TRUE,
	input_materialized BOOLEAN DEFAULT TRUE
) RETURNS TABLE(
    build_id VARCHAR,
	build_version VARCHAR,
    total_probes INT,
    isolated_covered_probes INT,
    isolated_missed_probes INT,
    isolated_probes_coverage_ratio FLOAT,
	aggregated_covered_probes INT,
    aggregated_missed_probes INT,
    aggregated_probes_coverage_ratio FLOAT,
    total_changes INT,
    isolated_tested_changes INT,
    aggregated_tested_changes INT
) AS $$
DECLARE
    use_materialized BOOLEAN;
BEGIN
    use_materialized := input_materialized
        AND input_baseline_build_id IS NULL
        AND input_chronological IS TRUE
        AND input_coverage_period_from IS NULL;

    RETURN QUERY
    WITH
	Builds AS (
		SELECT
			builds.id AS build_id,
			split_part(builds.id, ':', 3) AS version_id,
			builds.created_at AS build_created_at
		FROM raw_data.builds builds
		WHERE builds.group_id = input_group_id
          AND builds.app_id = input_app_id
		  --filter by branch
		  AND (input_branch IS NULL OR builds.branch = input_branch)
		  --filter by builds period from
		  AND (input_coverage_period_from IS NULL OR builds.created_at >= input_coverage_period_from)
		ORDER BY COALESCE(builds.committed_at, builds.created_at) DESC
		LIMIT input_builds_limit
	),
	BaselineMethods AS (
		SELECT
			baseline.signature,
            baseline.body_checksum
		FROM raw_data.view_methods_with_rules baseline
		WHERE baseline.build_id = input_baseline_build_id
	),
    TargetMethods AS (
        SELECT
            target.build_id,
            target.signature,
            target.body_checksum,
            target.probes_count,
			builds.build_created_at
        FROM raw_data.view_methods_with_rules target
		JOIN Builds builds ON builds.build_id = target.build_id
		LEFT JOIN BaselineMethods baseline ON baseline.signature = target.signature
        WHERE TRUE
		  --filter by baseline
		  AND (input_baseline_build_id IS NULL OR baseline.signature IS NULL OR baseline.body_checksum <> target.body_checksum)
    ),
    TargetMethodCoverage AS (
        SELECT
            target.build_id,
            target.signature,
            MAX(target.probes_count) AS probes_count,
            BIT_COUNT(BIT_OR(coverage.probes)) AS aggregated_covered_probes,
            BIT_COUNT(BIT_OR(CASE WHEN coverage.build_id = target.build_id THEN coverage.probes ELSE null END)) AS isolated_covered_probes
        FROM TargetMethods target
        LEFT JOIN raw_data.matview_methods_coverage_v3 coverage ON target.signature = coverage.signature
            AND target.body_checksum = coverage.body_checksum
            AND target.probes_count = coverage.probes_count
			--filter by group
			AND coverage.group_id = input_group_id
			--filter by app
            AND coverage.app_id = input_app_id
			--filter by only isolated coverage
            AND (input_aggregated_coverage IS TRUE OR coverage.build_id = target.build_id)
			--filter by chronological order
            AND (input_chronological IS FALSE OR coverage.build_created_at <= target.build_created_at)
            --filter by test tags
            AND (input_test_tag IS NULL OR input_test_tag = ANY(coverage.test_tags))
            --filter by branch
            AND (input_branch IS NULL OR coverage.branch = input_branch)
            --filter by env
            AND (input_env_id IS NULL OR coverage.env_id = input_env_id)
        GROUP BY target.build_id, target.signature
    ),
    CoverageGroupedByBuilds AS (
        SELECT
            coverage.build_id,
            SUM(coverage.probes_count) AS total_probes,
            SUM(coverage.aggregated_covered_probes) AS aggregated_covered_probes,
            SUM(coverage.isolated_covered_probes) AS isolated_covered_probes,
            COUNT(*) AS total_changes,
            SUM(
                CASE WHEN coverage.aggregated_covered_probes > 0 THEN 1 ELSE 0 END
            ) AS aggregated_tested_changes,
			SUM(
				CASE WHEN coverage.isolated_covered_probes > 0 THEN 1 ELSE 0 END
			) AS isolated_tested_changes
        FROM TargetMethodCoverage coverage
        GROUP BY coverage.build_id
    ),
    CoverageGroupedByBuildsMaterialized AS (
        SELECT
            coverage.build_id,
            MAX(coverage.total_probes) AS total_probes,
            BIT_COUNT(BIT_OR(coverage.probes)) AS aggregated_covered_probes,
            BIT_COUNT(BIT_OR(CASE
                WHEN coverage.build_id = coverage.coverage_build_id
                THEN coverage.probes
                ELSE NULL
            END)) AS isolated_covered_probes,
            MAX(coverage.total_methods) AS total_changes,
            BIT_COUNT(BIT_OR(coverage.methods_probes)) AS aggregated_tested_changes,
            BIT_COUNT(BIT_OR(CASE
                WHEN coverage.build_id = coverage.coverage_build_id
                THEN coverage.methods_probes
                ELSE NULL
            END)) AS isolated_tested_changes
        FROM raw_data.matview_builds_coverage_v3 coverage
        WHERE TRUE
          --filter by test tags
          AND (input_test_tag IS NULL OR input_test_tag = ANY(coverage.test_tags))
          --filter by branch
          AND (input_branch IS NULL OR coverage.branch = input_branch)
          --filter by env
          AND (input_env_id IS NULL OR coverage.env_id = input_env_id)
        GROUP BY coverage.build_id
    )
    SELECT
        coverage.build_id::VARCHAR,
        builds.version_id::VARCHAR AS build_version,
        coverage.total_probes::INT,
        COALESCE(coverage.isolated_covered_probes, 0)::INT AS isolated_covered_probes,
        (coverage.total_probes - COALESCE(coverage.isolated_covered_probes, 0))::INT AS isolated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.isolated_covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) AS isolated_probes_coverage_ratio,
        COALESCE(coverage.aggregated_covered_probes, 0)::INT AS aggregated_covered_probes,
        (coverage.total_probes - COALESCE(coverage.aggregated_covered_probes, 0))::INT AS aggregated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.aggregated_covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) AS aggregated_probes_coverage_ratio,
        coverage.total_changes::INT,
		COALESCE(coverage.isolated_tested_changes, 0)::INT AS isolated_tested_changes,
		COALESCE(coverage.aggregated_tested_changes, 0)::INT AS aggregated_tested_changes
    FROM (
        SELECT * FROM CoverageGroupedByBuildsMaterialized WHERE use_materialized
        UNION ALL
        SELECT * FROM CoverageGroupedByBuilds WHERE NOT use_materialized
    ) coverage
    JOIN Builds builds ON builds.build_id = coverage.build_id
    ORDER BY builds.build_created_at ASC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get coverage aggregated by methods of a given build
-- @param input_build_id VARCHAR: The ID of the build
-- @param input_baseline_build_id VARCHAR DEFAULT NULL: The ID of the baseline build (optional)
-- @param input_aggregated_coverage BOOLEAN DEFAULT FALSE: Flag to indicate if coverage collected from other builds should be used
-- @param input_test_tag VARCHAR DEFAULT NULL: Coverage collected from tests marked with this test tag (optional)
-- @param input_env_id VARCHAR DEFAULT NULL: Coverage collected from instances marked with this Environment ID (optional)
-- @param input_branch VARCHAR DEFAULT NULL: Coverage collected from builds of this branch (optional)
-- @param input_coverage_period_from TIMESTAMP DEFAULT NULL: Date from which to take into account the coverage (optional)
-- @param input_package_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by package name (optional)
-- @param input_class_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by class name (optional)
-- @param input_method_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by method name (optional)
-- @param input_chronological BOOLEAN DEFAULT TRUE: Flag to indicate if coverage should only be obtained in builds created earlier than the current one
-- @param input_materialized BOOLEAN DEFAULT TRUE: Flag to indicate if materialized views should be used
-- @returns TABLE: A table containing coverage aggregated by methods
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_methods_coverage_v2 CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_methods_coverage_v2(
    input_build_id VARCHAR,
    input_baseline_build_id VARCHAR DEFAULT NULL,
    input_aggregated_coverage BOOLEAN DEFAULT FALSE,
    input_test_tag VARCHAR DEFAULT NULL,
    input_env_id VARCHAR DEFAULT NULL,
    input_branch VARCHAR DEFAULT NULL,
    input_coverage_period_from TIMESTAMP DEFAULT NULL,
    input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL,
    input_chronological BOOLEAN DEFAULT TRUE,
    input_materialized BOOLEAN DEFAULT TRUE
)
RETURNS TABLE (
    build_id VARCHAR,
	signature VARCHAR,
    classname VARCHAR,
    name VARCHAR,
    params VARCHAR,
    return_type VARCHAR,
    probes_count INT,
    isolated_covered_probes INT,
    isolated_missed_probes INT,
    isolated_probes_coverage_ratio FLOAT,
   	aggregated_covered_probes INT,
    aggregated_missed_probes INT,
    aggregated_probes_coverage_ratio FLOAT,
    change_type VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    WITH
    BaselineMethods AS (
    		SELECT
    			baseline.signature,
                baseline.body_checksum
    		FROM raw_data.view_methods_with_rules baseline
    		WHERE baseline.build_id = input_baseline_build_id
    ),
    Methods AS (
        SELECT
			methods.build_id,
            methods.signature,
            methods.body_checksum,
            methods.probes_count,
            (CASE WHEN baseline.signature IS NULL THEN 'new' ELSE 'modified' END) AS change_type,
            builds.group_id,
            builds.app_id,
            builds.created_at AS build_created_at,
			methods.classname,
        	methods.name,
        	methods.params,
        	methods.return_type
        FROM raw_data.view_methods_with_rules methods
        JOIN raw_data.builds builds ON builds.id = methods.build_id
        LEFT JOIN BaselineMethods baseline ON baseline.signature = methods.signature
        WHERE methods.build_id = input_build_id
            --filter by package pattern
            AND (input_package_name_pattern IS NULL OR methods.classname LIKE input_package_name_pattern)
            --filter by class pattern
            AND (input_class_name_pattern IS NULL OR methods.classname LIKE input_class_name_pattern)
            --filter by method pattern
            AND (input_method_name_pattern IS NULL OR methods.name LIKE input_method_name_pattern)
            --filter by baseline
            AND (input_baseline_build_id IS NULL OR baseline.signature IS NULL OR baseline.body_checksum <> methods.body_checksum)
    ),
    MethodCoverage AS (
        SELECT
            methods.build_id,
            methods.signature,
            MAX(methods.probes_count) AS probes_count,
            BIT_COUNT(BIT_OR(coverage.probes)) AS aggregated_covered_probes,
            BIT_COUNT(BIT_OR(CASE WHEN coverage.build_id = methods.build_id THEN coverage.probes ELSE null END)) AS isolated_covered_probes
        FROM raw_data.view_methods_coverage_v2 coverage
        JOIN Methods methods ON coverage.signature = methods.signature
          AND coverage.body_checksum = methods.body_checksum
          AND coverage.probes_count = methods.probes_count
        WHERE coverage.group_id = methods.group_id
          AND coverage.app_id = methods.app_id
          -- filters by coverage
          AND (input_aggregated_coverage IS TRUE OR coverage.build_id = methods.build_id)
          AND (input_chronological IS FALSE OR coverage.build_created_at <= methods.build_created_at)
          AND (input_test_tag IS NULL OR input_test_tag = ANY(coverage.test_tags))
          AND (input_branch IS NULL OR coverage.branch = input_branch)
		  AND (input_env_id IS NULL OR coverage.env_id = input_env_id)
		  AND (input_coverage_period_from IS NULL OR coverage.created_at >= input_coverage_period_from)
        GROUP BY methods.build_id, methods.signature
    ),
    MaterializedMethodCoverage AS (
        SELECT
            methods.build_id,
            methods.signature,
            MAX(methods.probes_count) AS probes_count,
            BIT_COUNT(BIT_OR(coverage.probes)) AS aggregated_covered_probes,
            BIT_COUNT(BIT_OR(CASE WHEN coverage.build_id = methods.build_id THEN coverage.probes ELSE null END)) AS isolated_covered_probes
        FROM raw_data.matview_methods_coverage_v3 coverage
        JOIN Methods methods ON coverage.signature = methods.signature
          AND coverage.body_checksum = methods.body_checksum
          AND coverage.probes_count = methods.probes_count
        WHERE coverage.group_id = methods.group_id
          AND coverage.app_id = methods.app_id
          -- filters by coverage
          AND (input_aggregated_coverage IS TRUE OR coverage.build_id = methods.build_id)
          AND (input_chronological IS FALSE OR coverage.build_created_at <= methods.build_created_at)
          AND (input_test_tag IS NULL OR input_test_tag = ANY(coverage.test_tags))
          AND (input_branch IS NULL OR coverage.branch = input_branch)
          AND (input_env_id IS NULL OR coverage.env_id = input_env_id)
        GROUP BY methods.build_id, methods.signature
    )
    SELECT
        methods.build_id::VARCHAR,
    	methods.signature::VARCHAR,
        methods.classname,
        methods.name,
        methods.params,
        methods.return_type,
        methods.probes_count::INT,
        COALESCE(coverage.isolated_covered_probes, 0)::INT AS isolated_covered_probes,
        (methods.probes_count - COALESCE(coverage.isolated_covered_probes, 0))::INT AS isolated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.isolated_covered_probes, 0) AS FLOAT) / methods.probes_count, 0.0) AS isolated_probes_coverage_ratio,
        COALESCE(coverage.aggregated_covered_probes, 0)::INT AS aggregated_covered_probes,
        (methods.probes_count - COALESCE(coverage.aggregated_covered_probes, 0))::INT AS aggregated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.aggregated_covered_probes, 0) AS FLOAT) / methods.probes_count, 0.0) AS aggregated_probes_coverage_ratio,
		methods.change_type::VARCHAR
    FROM Methods methods
    LEFT JOIN (
        SELECT * FROM MethodCoverage WHERE NOT input_materialized
        UNION ALL
        SELECT * FROM MaterializedMethodCoverage WHERE input_materialized
    ) coverage ON coverage.build_id = methods.build_id AND methods.signature = coverage.signature;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get builds changes compared to a baseline build
-- @param input_baseline_build_id VARCHAR: The ID of the baseline build
-- @param input_build_id VARCHAR DEFAULT NULL: The ID of the build to compare with the baseline (optional)
-- @param input_materialized BOOLEAN DEFAULT TRUE: Flag to indicate if materialized views should be used
-- @returns TABLE: A table containing the builds with changes compared to the baseline
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_builds_compared_to_baseline CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_builds_compared_to_baseline(
    input_baseline_build_id VARCHAR,
	input_build_id VARCHAR DEFAULT NULL,
    input_materialized BOOLEAN DEFAULT TRUE
)
RETURNS TABLE (
    build_id VARCHAR,
	equal BIGINT,
    modified BIGINT,
	added BIGINT,
	deleted BIGINT,
    identity_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
    WITH
    BaselineMethods AS (
		SELECT
			m.signature,
			m.body_checksum
		FROM raw_data.view_methods_with_rules m
		WHERE m.build_id = input_baseline_build_id
    ),
	MaterializedBaselineMethods AS (
		SELECT
			m.signature,
			m.body_checksum
		FROM raw_data.matview_methods_with_rules m
		WHERE m.build_id = input_baseline_build_id
    ),
	TargetMethods AS (
		SELECT
		    m.build_id,
			m.signature,
			m.body_checksum
		FROM raw_data.view_methods_with_rules m
		WHERE m.group_id = split_part(input_baseline_build_id, ':', 1)
          AND m.app_id = split_part(input_baseline_build_id, ':', 2)
		  AND m.build_id <> input_baseline_build_id
		  -- filter by build
		  AND (input_build_id IS NULL OR m.build_id = input_build_id)
    ),
	MaterializedTargetMethods AS (
		SELECT
		    m.build_id,
			m.signature,
			m.body_checksum
		FROM raw_data.matview_methods_with_rules m
		WHERE m.group_id = split_part(input_baseline_build_id, ':', 1)
          AND m.app_id = split_part(input_baseline_build_id, ':', 2)
		  AND m.build_id <> input_baseline_build_id
		  -- filter by build
		  AND (input_build_id IS NULL OR m.build_id = input_build_id)
    ),
    ChangedMethods AS (
        SELECT
			m.build_id,
            m.signature,
            m.body_checksum,
			baseline.body_checksum AS baseline_body_checksum
		FROM TargetMethods m
        LEFT JOIN BaselineMethods baseline ON baseline.signature = m.signature
    ),
	MaterializedChangedMethods AS (
        SELECT
			m.build_id,
            m.signature,
            m.body_checksum,
			baseline.body_checksum AS baseline_body_checksum
		FROM MaterializedTargetMethods m
        LEFT JOIN MaterializedBaselineMethods baseline ON baseline.signature = m.signature
    ),
	ChangedBuilds AS (
		SELECT
		  	m.build_id,
			SUM(
				CASE WHEN m.body_checksum = m.baseline_body_checksum THEN 1 ELSE 0 END
			) AS equal,
			SUM(
				CASE WHEN m.body_checksum <> m.baseline_body_checksum THEN 1 ELSE 0 END
			) AS modified,
			SUM(
				CASE WHEN m.baseline_body_checksum IS NULL THEN 1 ELSE 0 END
			) AS added,
			((SELECT COUNT(*) from BaselineMethods) - SUM(
				CASE WHEN m.baseline_body_checksum IS NOT NULL THEN 1 ELSE 0 END
			)) AS deleted
		FROM (
			SELECT * FROM MaterializedChangedMethods WHERE input_materialized
			UNION ALL
			SELECT * FROM ChangedMethods WHERE NOT input_materialized
		) m
		GROUP BY m.build_id
	)
	SELECT
    	builds.build_id,
     	builds.equal,
	 	builds.modified,
	 	builds.added,
	 	builds.deleted,
	  	(1 - (builds.modified + builds.added + builds.deleted)::FLOAT / (SELECT COUNT(*) FROM BaselineMethods)::FLOAT) AS identity_ratio
	FROM ChangedBuilds builds
	ORDER BY identity_ratio DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function for comparing unique code coverage of tests
-- @param input_build_id VARCHAR: The ID of the build
-- @param input_baseline_build_id VARCHAR DEFAULT NULL: The ID of the baseline build (optional)
-- @param input_package_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by package name (optional)
-- @param input_class_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by class name (optional)
-- @param input_method_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by method name (optional)
-- @param input_target_test_session_id VARCHAR DEFAULT NULL: The ID of the target test session (optional)
-- @param input_target_test_launch_id VARCHAR DEFAULT NULL: The ID of the target test launch (optional)
-- @param input_target_test_tag VARCHAR DEFAULT NULL: The tag of the target test (optional)
-- @param input_target_test_path_pattern VARCHAR DEFAULT NULL: Pattern to filter by target test path (optional)
-- @param input_comparable_test_session_id VARCHAR DEFAULT NULL: The ID of the comparable test session (optional)
-- @param input_comparable_test_launch_id VARCHAR DEFAULT NULL: The ID of the comparable test launch (optional)
-- @param input_comparable_test_tag VARCHAR DEFAULT NULL: The tag of the comparable test (optional)
-- @param input_comparable_test_path_pattern VARCHAR DEFAULT NULL: Pattern to filter by comparable test path (optional)
-- @param input_coverage_in_other_builds BOOLEAN DEFAULT FALSE: Flag to indicate if coverage from other builds should be used
-- @param input_coverage_env_id VARCHAR DEFAULT NULL: The ID of the environment for coverage (optional)
-- @param input_coverage_branch VARCHAR DEFAULT NULL: The branch for coverage (optional)
-- @param input_coverage_period_from TIMESTAMP DEFAULT NULL: Date from which to take into account the coverage (optional)
-- @param input_coverage_chronological BOOLEAN DEFAULT TRUE: Flag to indicate if coverage should only be obtained in builds created earlier than the current one
-- @returns TABLE: A table containing the comparison of unique code coverage of tests
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_build_tests_comparison CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_build_tests_comparison(
    input_build_id VARCHAR,
    input_baseline_build_id VARCHAR DEFAULT NULL,
	input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL,

    input_target_test_session_id VARCHAR DEFAULT NULL,
	input_target_test_launch_id VARCHAR DEFAULT NULL,
	input_target_test_tag VARCHAR DEFAULT NULL,
	input_target_test_path_pattern VARCHAR DEFAULT NULL,

	input_comparable_test_session_id VARCHAR DEFAULT NULL,
	input_comparable_test_launch_id VARCHAR DEFAULT NULL,
	input_comparable_test_tag VARCHAR DEFAULT NULL,
	input_comparable_test_path_pattern VARCHAR DEFAULT NULL,

	input_coverage_in_other_builds BOOLEAN DEFAULT FALSE,
	input_coverage_env_id VARCHAR DEFAULT NULL,
    input_coverage_branch VARCHAR DEFAULT NULL,
    input_coverage_period_from TIMESTAMP DEFAULT NULL,
	input_coverage_chronological BOOLEAN DEFAULT TRUE,

    input_materialized BOOLEAN DEFAULT TRUE
)
RETURNS TABLE (
    build_id VARCHAR,
    total_probes INT,
    covered_probes INT,
	unique_covered_probes INT,
	missed_probes INT,
	probes_coverage_ratio FLOAT,
	unique_probes_coverage_ratio FLOAT
) AS $$
DECLARE
    use_materialized BOOLEAN;
BEGIN
	use_materialized := input_materialized IS TRUE
        AND input_baseline_build_id IS NULL
        AND input_package_name_pattern IS NULL
        AND input_class_name_pattern IS NULL
        AND input_method_name_pattern IS NULL
        AND input_coverage_chronological IS TRUE
		AND input_coverage_period_from IS NULL;

    RETURN QUERY
	WITH
	    BaselineMethods AS (
			SELECT
				baseline.signature,
				baseline.body_checksum
			FROM raw_data.matview_methods_with_rules baseline
			WHERE baseline.build_id = input_baseline_build_id
	    ),
		Builds AS (
			SELECT
				methods.build_id,
				SUM(methods.probes_count) AS total_probes
			FROM raw_data.matview_methods_with_rules methods
			LEFT JOIN BaselineMethods baseline ON baseline.signature = methods.signature
			WHERE methods.build_id = input_build_id
			  --Filters by methods
              AND (input_package_name_pattern IS NULL OR methods.classname LIKE input_package_name_pattern)
              AND (input_class_name_pattern IS NULL OR methods.classname LIKE input_class_name_pattern)
              AND (input_method_name_pattern IS NULL OR methods.name LIKE input_method_name_pattern)
			  AND (input_baseline_build_id IS NULL OR baseline.signature IS NULL OR baseline.body_checksum <> methods.body_checksum)
			GROUP BY methods.build_id
		),
		Methods AS (
			SELECT
			    builds.group_id,
				builds.app_id,
				methods.build_id,
				methods.signature,
				methods.body_checksum,
				methods.probes_count,
				(SUM(methods.probes_count) OVER (PARTITION BY methods.build_id ORDER BY methods.signature)) - methods.probes_count + 1 AS probes_start,
				builds.created_at AS build_created_at
			FROM raw_data.matview_methods_with_rules methods
			JOIN raw_data.builds builds ON builds.id = methods.build_id
			LEFT JOIN BaselineMethods baseline ON baseline.signature = methods.signature
			WHERE methods.build_id = input_build_id
				--Filters by methods
              AND (input_package_name_pattern IS NULL OR methods.classname LIKE input_package_name_pattern)
              AND (input_class_name_pattern IS NULL OR methods.classname LIKE input_class_name_pattern)
              AND (input_method_name_pattern IS NULL OR methods.name LIKE input_method_name_pattern)
              AND (input_baseline_build_id IS NULL OR baseline.signature IS NULL OR baseline.body_checksum <> methods.body_checksum)
			ORDER BY methods.build_id, methods.signature
		),
		MethodsCoverage AS (
			SELECT
	            methods.build_id,
	            methods.signature,
				methods.probes_count,
				methods.probes_start,
				coverage.probes AS probes,
				coverage.test_launch_id,
				coverage.test_session_id,
				coverage.test_tags,
				coverage.test_path,
				coverage.created_at,
				coverage.build_created_at
	        FROM raw_data.view_methods_coverage_v2 coverage
			JOIN Methods methods ON coverage.signature = methods.signature
	            AND coverage.body_checksum = methods.body_checksum
	            AND coverage.probes_count = methods.probes_count
			WHERE (coverage.build_id = methods.build_id
					OR (input_coverage_in_other_builds IS TRUE
						AND coverage.group_id = methods.group_id
						AND coverage.app_id = methods.app_id
				))
	            --Filters by coverage
	            AND (input_coverage_chronological IS FALSE OR coverage.build_created_at <= methods.build_created_at)
				AND (input_coverage_branch IS NULL OR coverage.branch = input_coverage_branch)
				AND (input_coverage_env_id IS NULL OR coverage.env_id = input_coverage_env_id)
				AND (input_coverage_period_from IS NULL OR coverage.created_at >= input_coverage_period_from)
		),
		TargetMethodsCoverage AS (
	        SELECT
	            coverage.build_id,
	            coverage.signature,
				MAX(coverage.probes_count) AS probes_count,
				MAX(coverage.probes_start) AS probes_start,
	            BIT_OR(coverage.probes) AS probes
	        FROM MethodsCoverage coverage
			WHERE TRUE
				--Filters by target tests
				AND (input_target_test_session_id IS NULL OR coverage.test_session_id = input_target_test_session_id)
				AND (input_target_test_tag IS NULL OR  input_target_test_tag = ANY(coverage.test_tags))
	        GROUP BY coverage.build_id, coverage.signature
			ORDER BY coverage.build_id, coverage.signature
	    ),
		ComparableMethodsCoverage AS (
	        SELECT
	            coverage.build_id,
	            coverage.signature,
				MAX(coverage.probes_count) AS probes_count,
				MAX(coverage.probes_start) AS probes_start,
	            BIT_OR(coverage.probes) AS probes
	        FROM MethodsCoverage coverage
			WHERE TRUE
				--Filters by comparable tests
				AND (input_comparable_test_session_id IS NULL OR coverage.test_session_id = input_comparable_test_session_id)
				AND (input_comparable_test_tag IS NULL OR  input_comparable_test_tag = ANY(coverage.test_tags))

				--Filters to exclude target tests
				AND (input_target_test_session_id IS NULL OR coverage.test_session_id <> input_target_test_session_id)
				AND (input_target_test_tag IS NULL OR input_target_test_tag <> ANY(coverage.test_tags))
				AND (input_target_test_path_pattern IS NULL OR coverage.test_path NOT LIKE input_target_test_path_pattern)
	        GROUP BY coverage.build_id, coverage.signature
			ORDER BY coverage.build_id, coverage.signature
	    ),
		TargetBuildCoverage AS (
			SELECT
				coverage.build_id,
				raw_data.CONCAT_VARBIT(coverage.probes::VARBIT, coverage.probes_start::INT) AS probes
			FROM TargetMethodsCoverage coverage
			GROUP BY coverage.build_id
		),
		ComparableBuildCoverage AS (
			SELECT
				coverage.build_id,
				raw_data.CONCAT_VARBIT(coverage.probes::VARBIT, coverage.probes_start::INT) AS probes
			FROM ComparableMethodsCoverage coverage
			GROUP BY coverage.build_id
		),
	    AugmentedTargetBuildCoverage AS (
			SELECT
				coverage.build_id,
				coverage.probes || (REPEAT('0', (builds.total_probes - BIT_LENGTH(coverage.probes))::INT)::VARBIT) AS probes,
				builds.total_probes
			FROM TargetBuildCoverage coverage
			JOIN Builds builds ON builds.build_id = coverage.build_id
			WHERE BIT_COUNT(coverage.probes) > 0
		),
	    PaddedComparableBuildCoverage AS (
			SELECT
				coverage.build_id,
				coverage.probes || (REPEAT('0', (builds.total_probes - BIT_LENGTH(coverage.probes))::INT)::VARBIT) AS probes,
				builds.total_probes
			FROM ComparableBuildCoverage coverage
			JOIN Builds builds ON builds.build_id = coverage.build_id
			WHERE BIT_COUNT(coverage.probes) > 0
		),
	    MaterializedTargetBuildCoverage AS (
			SELECT
				coverage.build_id,
				BIT_OR(coverage.probes) AS probes,
				MAX(coverage.total_probes) AS total_probes
			FROM raw_data.matview_builds_coverage_v3 coverage
			JOIN raw_data.builds coverage_builds ON coverage_builds.id = coverage.coverage_build_id
			WHERE coverage.build_id = input_build_id
				AND (input_coverage_in_other_builds IS FALSE
					OR (coverage_builds.group_id = split_part(input_build_id, ':', 1)
					AND coverage_builds.app_id = split_part(input_build_id, ':', 2)))
	            --Filters by coverage
	            AND (input_coverage_branch IS NULL OR coverage.branch = input_coverage_branch)
				AND (input_coverage_env_id IS NULL OR coverage.env_id = input_coverage_env_id)

				--Filters by target tests
				AND (input_target_test_session_id IS NULL OR coverage.test_session_id = input_target_test_session_id)
				AND (input_target_test_tag IS NULL OR input_target_test_tag = ANY(coverage.test_tags))
			GROUP BY coverage.build_id
		),
	    MaterializedComparableBuildCoverage AS (
			SELECT
				coverage.build_id,
				BIT_OR(coverage.probes) AS probes,
				MAX(coverage.total_probes) AS total_probes
			FROM raw_data.matview_builds_coverage_v2 coverage
			JOIN raw_data.builds coverage_builds ON coverage_builds.id = coverage.coverage_build_id
			WHERE coverage.build_id = input_build_id
				AND (input_coverage_in_other_builds IS FALSE
					OR (coverage_builds.group_id = split_part(input_build_id, ':', 1)
					AND coverage_builds.app_id = split_part(input_build_id, ':', 2)))
	            --Filters by coverage
	            AND (input_coverage_branch IS NULL OR coverage.branch = input_coverage_branch)
				AND (input_coverage_env_id IS NULL OR coverage.env_id = input_coverage_env_id)

				--Filters by comparable tests
				AND (input_comparable_test_session_id IS NULL OR coverage.test_session_id = input_comparable_test_session_id)
				AND (input_comparable_test_tag IS NULL OR input_comparable_test_tag = ANY(coverage.test_tags))

				--Filters to exclude target tests
				AND (input_target_test_session_id IS NULL OR coverage.test_session_id <> input_target_test_session_id)
				AND (input_target_test_tag IS NULL OR input_target_test_tag <> ANY(coverage.test_tags))
			GROUP BY coverage.build_id
		),
		UniqueTargetCoverage AS (
			SELECT
				coverage.build_id,
				coverage.total_probes,
				BIT_COUNT(coverage.probes) AS covered_probes,
				BIT_COUNT(coverage.probes & ~(SELECT probes FROM AugmentedComparableBuildCoverage)) AS unique_covered_probes
			FROM AugmentedTargetBuildCoverage coverage
		),
		MaterializedUniqueTargetCoverage AS (
			SELECT
				coverage.build_id,
				coverage.total_probes,
				BIT_COUNT(coverage.probes) AS covered_probes,
				BIT_COUNT(coverage.probes & ~(SELECT probes FROM MaterializedComparableBuildCoverage)) AS unique_covered_probes
			FROM MaterializedTargetBuildCoverage coverage
		)

		SELECT
			coverage.build_id::VARCHAR,
			coverage.total_probes::INT,
			coverage.covered_probes::INT,
			coverage.unique_covered_probes::INT,
			(coverage.total_probes - coverage.covered_probes)::INT AS missed_probes,
			COALESCE(CAST(COALESCE(coverage.covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) AS probes_coverage_ratio,
			COALESCE(CAST(COALESCE(coverage.unique_covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) AS unique_probes_coverage_ratio
		FROM (
			SELECT * FROM MaterializedUniqueTargetCoverage WHERE use_materialized
			UNION ALL
			SELECT * FROM UniqueTargetCoverage WHERE NOT use_materialized
		) coverage;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get builds changes compared to a baseline build
-- @param input_build_id VARCHAR DEFAULT NULL: The ID of the build to compare with the baseline (optional)
-- @param input_baseline_build_id VARCHAR DEFAULT NULL: The ID of the baseline build (optional)
-- @param input_chronological BOOLEAN DEFAULT TRUE: Flag indicating whether to only search for builds earlier than the current one.
-- @param input_materialized BOOLEAN DEFAULT TRUE: Flag to indicate if materialized views should be used
-- @returns TABLE: A table containing the builds with changes compared to the baseline
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_builds_compared_to_baseline_v2 CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_builds_compared_to_baseline_v2(
    input_build_id VARCHAR DEFAULT NULL,
    input_baseline_build_id VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    build_id VARCHAR,
	baseline_build_id VARCHAR,
	equal BIGINT,
    modified BIGINT,
	added BIGINT,
	deleted BIGINT,
	total_methods BIGINT,
	total_probes BIGINT,
	baseline_total_methods BIGINT,
	baseline_total_probes BIGINT,
	identity_ratio FLOAT,
	probes VARBIT,
	methods_probes VARBIT
) AS $$
BEGIN
    RETURN QUERY
    WITH
    BaselineMethods AS (
		SELECT
		    m.group_id,
			m.app_id,
			m.build_id,
			m.signature,
			m.body_checksum,
			m.probes_count
		FROM raw_data.matview_methods_with_rules m
		WHERE (input_baseline_build_id IS NULL OR m.build_id = input_baseline_build_id)
    ),
	TargetMethods AS (
		SELECT
			m.group_id,
			m.app_id,
		    m.build_id,
			m.signature,
			m.body_checksum,
			m.probes_count,
			m.probes_start,
			m.method_num
		FROM raw_data.matview_methods_with_rules m
		WHERE (input_build_id IS NULL OR m.build_id = input_build_id)
    ),
    MethodsComparison AS (
        SELECT
			target.build_id,
            target.signature,
            target.body_checksum,
			target.probes_count,
			target.probes_start,
			target.method_num,
			baseline.build_id AS baseline_build_id,
			baseline.body_checksum AS baseline_body_checksum,
			(CASE WHEN target.body_checksum = baseline.body_checksum AND target.probes_count = baseline.probes_count THEN 1 ELSE 0 END) AS equal,
			(CASE WHEN target.body_checksum <> baseline.body_checksum OR target.probes_count <> baseline.probes_count THEN 1 ELSE 0 END) AS modified
		FROM TargetMethods target
		JOIN BaselineMethods baseline ON baseline.group_id = target.group_id
			AND baseline.app_id = target.app_id
		  	AND baseline.signature = target.signature
        JOIN raw_data.builds target_builds ON target_builds.id = target.build_id
		JOIN raw_data.builds baseline_builds ON baseline_builds.id = baseline.build_id
		WHERE target.build_id <> baseline.build_id
			--filter by chronology
			AND baseline_builds.created_at <= target_builds.created_at
		ORDER BY target.build_id, baseline.build_id, target.signature
    ),
	BuildsComparison AS (
		SELECT
		  	m.build_id,
			m.baseline_build_id,
			SUM(m.equal) AS equal,
			SUM(m.modified) AS modified,
			raw_data.CONCAT_VARBIT(REPEAT(m.equal::VARCHAR, m.probes_count)::VARBIT, m.probes_start::INT) AS probes,
			raw_data.CONCAT_VARBIT(REPEAT(m.equal::VARCHAR, 1)::VARBIT, m.method_num::INT) AS methods_probes
		FROM MethodsComparison m
		GROUP BY m.build_id, m.baseline_build_id
	),
	PaddedBuildsComparison AS (
		SELECT
		  	m.build_id,
			m.baseline_build_id,
			m.equal,
			m.modified,
			target_b.total_methods - m.modified - m.equal AS added,
	 		baseline_b.total_methods - m.modified - m.equal AS deleted,
			target_b.total_methods AS total_methods,
			target_b.total_probes AS total_probes,
			baseline_b.total_methods AS baseline_total_methods,
			baseline_b.total_probes AS baseline_total_probes,
			m.probes || (REPEAT('0', (target_b.total_probes - BIT_LENGTH(m.probes))::INT)::VARBIT) AS probes,
			m.methods_probes || REPEAT('0', (target_b.total_methods - BIT_LENGTH(m.methods_probes))::INT)::VARBIT AS methods_probes
		FROM BuildsComparison m
		JOIN raw_data.matview_builds target_b ON target_b.build_id = m.build_id
		JOIN raw_data.matview_builds baseline_b ON baseline_b.build_id = m.baseline_build_id
	),
	MaterializedBuildsComparison AS (
		SELECT
	    	builds.build_id,
			builds.baseline_build_id,
	     	builds.equal,
		 	builds.modified,
			builds.added,
		 	builds.deleted,
			builds.total_methods,
			builds.total_probes,
			builds.baseline_total_methods,
			builds.baseline_total_probes,
			(builds.equal::FLOAT / builds.total_methods::FLOAT) AS identity_ratio,
		  	builds.probes AS probes,
			builds.methods_probes AS methods_probes
		FROM raw_data.matview_builds_comparison builds
		WHERE TRUE
			AND (input_baseline_build_id IS NULL OR builds.baseline_build_id = input_baseline_build_id)
		  	AND (input_build_id IS NULL OR builds.build_id = input_build_id)
	)
	SELECT
    	builds.build_id,
		builds.baseline_build_id,
     	builds.equal,
	 	builds.modified,
		builds.added,
	 	builds.deleted,
		builds.total_methods,
		builds.total_probes,
		builds.baseline_total_methods,
		builds.baseline_total_probes,
		(builds.equal::FLOAT / builds.total_methods::FLOAT) AS identity_ratio,
	  	builds.probes AS probes,
		builds.methods_probes AS methods_probes
	FROM MaterializedBuildsComparison builds
	ORDER BY identity_ratio DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get coverage details aggregated by a given build
-- @param input_build_id VARCHAR: The ID of the build
-- @param input_baseline_build_id VARCHAR DEFAULT NULL: The ID of the baseline build (optional)
-- @param input_coverage_in_other_builds BOOLEAN DEFAULT FALSE: Flag to indicate if coverage from other builds should be used
-- @param input_coverage_branch VARCHAR DEFAULT NULL: The branch for coverage (optional)
-- @param input_coverage_env_id VARCHAR DEFAULT NULL: The ID of the environment for coverage (optional)
-- @param input_coverage_test_tag VARCHAR DEFAULT NULL: The tag of the test for coverage (optional)
-- @param input_coverage_test_session_id VARCHAR DEFAULT NULL: The ID of the test session for coverage (optional)
-- @returns TABLE: A table containing build coverage details
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_build_coverage_v3 CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_build_coverage_v3(
    input_build_id VARCHAR,
    input_baseline_build_id VARCHAR DEFAULT NULL,
    input_coverage_in_other_builds BOOLEAN DEFAULT FALSE,
    input_coverage_branch VARCHAR DEFAULT NULL,
    input_coverage_env_id VARCHAR DEFAULT NULL,
    input_coverage_test_tag VARCHAR DEFAULT NULL,
    input_coverage_test_session_id VARCHAR DEFAULT NULL
) RETURNS TABLE(
    build_id VARCHAR,

	total_probes INT,
    isolated_covered_probes INT,
    isolated_missed_probes INT,
    isolated_probes_coverage_ratio FLOAT,
	aggregated_covered_probes INT,
    aggregated_missed_probes INT,
    aggregated_probes_coverage_ratio FLOAT,

	total_methods INT,
    isolated_tested_methods INT,
	isolated_missed_methods INT,
    isolated_methods_coverage_ratio FLOAT,
    aggregated_tested_methods INT,
	aggregated_missed_methods INT,
    aggregated_methods_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
    WITH
	BuildsCoverage AS (
		SELECT
            coverage.build_id,
            coverage.coverage_build_id,
            BIT_OR(coverage.probes) AS probes,
            BIT_OR(coverage.methods_probes) AS methods_probes,
            MAX(coverage.total_probes) AS total_probes,
            MAX(coverage.total_methods) AS total_methods
        FROM raw_data.matview_builds_coverage_v3 coverage
		WHERE coverage.build_id = input_build_id
		  	--Filters by coverage
            AND (input_coverage_in_other_builds IS TRUE OR coverage.coverage_build_id = input_build_id)
		    AND (input_coverage_branch IS NULL OR coverage.branch = input_coverage_branch)
			AND (input_coverage_env_id IS NULL OR coverage.env_id = input_coverage_env_id)
			--TODO How to identify test sessions of the same test task in the same build?
			AND (input_coverage_test_session_id IS NULL OR coverage.build_id <> coverage.coverage_build_id OR coverage.test_session_id = input_coverage_test_session_id)
		GROUP BY coverage.build_id, coverage.coverage_build_id
	),
	BuildsCoverageComparedToBaseline AS (
		SELECT
            coverage.build_id,
            coverage.coverage_build_id,
            coverage.probes & ~baseline.probes AS probes,
            coverage.methods_probes & ~baseline.methods_probes AS method_probes,
            BIT_COUNT(~baseline.probes) AS total_probes,
            BIT_COUNT(~baseline.methods_probes) AS total_methods
        FROM BuildsCoverage coverage
		JOIN raw_data.get_builds_compared_to_baseline_v2(
			input_build_id => input_build_id,
			input_baseline_build_id => input_baseline_build_id
		) baseline ON input_baseline_build_id IS NOT NULL
	),
    TargetBuildsCoverage AS (
        SELECT
            coverage.build_id,
            BIT_COUNT(BIT_OR(coverage.probes)) AS aggregated_covered_probes,
            BIT_COUNT(BIT_OR(CASE
                WHEN coverage.build_id = coverage.coverage_build_id
                THEN coverage.probes
                ELSE NULL
            END)) AS isolated_covered_probes,
            BIT_COUNT(BIT_OR(coverage.methods_probes)) AS aggregated_tested_methods,
            BIT_COUNT(BIT_OR(CASE
                WHEN coverage.build_id = coverage.coverage_build_id
                THEN coverage.methods_probes
                ELSE NULL
            END)) AS isolated_tested_methods,
            MAX(coverage.total_probes) AS total_probes,
            MAX(coverage.total_methods) AS total_methods
        FROM (
			SELECT * FROM BuildsCoverage WHERE input_baseline_build_id IS NULL
			UNION ALL
			SELECT * FROM BuildsCoverageComparedToBaseline WHERE input_baseline_build_id IS NOT NULL
		) coverage
        GROUP BY coverage.build_id
    )
	SELECT
        coverage.build_id::VARCHAR,

		coverage.total_probes::INT,
        COALESCE(coverage.isolated_covered_probes, 0)::INT AS isolated_covered_probes,
        (coverage.total_probes - COALESCE(coverage.isolated_covered_probes, 0))::INT AS isolated_missed_probes,
        (CASE WHEN coverage.total_probes > 0 THEN COALESCE(CAST(COALESCE(coverage.isolated_covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) ELSE 0.0 END) AS isolated_probes_coverage_ratio,
        COALESCE(coverage.aggregated_covered_probes, 0)::INT AS aggregated_covered_probes,
        (coverage.total_probes - COALESCE(coverage.aggregated_covered_probes, 0))::INT AS aggregated_missed_probes,
        (CASE WHEN coverage.total_probes > 0 THEN COALESCE(CAST(COALESCE(coverage.aggregated_covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) ELSE 0.0 END) AS aggregated_probes_coverage_ratio,

		coverage.total_methods::INT,
		COALESCE(coverage.isolated_tested_methods, 0)::INT AS isolated_tested_methods,
		(coverage.total_methods - COALESCE(coverage.isolated_tested_methods, 0))::INT AS isolated_missed_methods,
		(CASE WHEN coverage.total_methods > 0 THEN COALESCE(CAST(COALESCE(coverage.isolated_tested_methods, 0) AS FLOAT) / coverage.total_methods, 0.0) ELSE 0.0 END) AS isolated_methods_coverage_ratio,
		COALESCE(coverage.aggregated_tested_methods, 0)::INT AS aggregated_tested_methods,
		(coverage.total_methods - COALESCE(coverage.aggregated_tested_methods, 0))::INT AS aggregated_missed_methods,
        (CASE WHEN coverage.total_methods > 0 THEN COALESCE(CAST(COALESCE(coverage.aggregated_tested_methods, 0) AS FLOAT) / coverage.total_methods, 0.0) ELSE 0.0 END) AS aggregated_methods_coverage_ratio
    FROM TargetBuildsCoverage coverage;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;


-----------------------------------------------------------------
-- Function to get information about trends in build coverage of a specific branch
-- @param input_group_id VARCHAR: The ID of the group
-- @param input_app_id VARCHAR: The ID of the application
-- @param input_builds_branch VARCHAR DEFAULT NULL: The branch of the builds (optional)
-- @param input_builds_limit INT DEFAULT 10: Limit on the number of builds to return
-- @param input_baseline_build_id VARCHAR DEFAULT NULL: The ID of the baseline build (optional)
-- @param input_coverage_in_other_builds BOOLEAN DEFAULT FALSE: Flag to indicate if aggregated coverage should be used
-- @param input_coverage_test_tag VARCHAR DEFAULT NULL: Coverage collected from tests marked with this test tag (optional)
-- @param input_coverage_env_id VARCHAR DEFAULT NULL: Coverage collected from instances marked with this Environment ID (optional)
-- @param input_coverage_branch VARCHAR DEFAULT NULL: Only coverage from this branch are collected (optional)
-- @returns TABLE: A table containing build coverage trends
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_build_coverage_trends_v3 CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_build_coverage_trends_v3(
    input_group_id VARCHAR,
    input_app_id VARCHAR,
	input_builds_branch VARCHAR DEFAULT NULL,
	input_builds_limit INT DEFAULT 10,
	input_baseline_build_id VARCHAR DEFAULT NULL,
	input_coverage_in_other_builds BOOLEAN DEFAULT FALSE,
	input_coverage_test_tag VARCHAR DEFAULT NULL,
	input_coverage_env_id VARCHAR DEFAULT NULL,
	input_coverage_branch VARCHAR DEFAULT NULL
) RETURNS TABLE(
    build_id VARCHAR,
	build_version VARCHAR,
    total_probes INT,
    isolated_covered_probes INT,
    isolated_missed_probes INT,
    isolated_probes_coverage_ratio FLOAT,
	aggregated_covered_probes INT,
    aggregated_missed_probes INT,
    aggregated_probes_coverage_ratio FLOAT,
    total_changes INT,
    isolated_tested_changes INT,
    aggregated_tested_changes INT
) AS $$
BEGIN
    RETURN QUERY
    WITH
	Builds AS (
		SELECT
			builds.id AS build_id,
			split_part(builds.id, ':', 3) AS version_id,
			COALESCE(builds.committed_at, builds.created_at) AS build_created_at
		FROM raw_data.builds builds
		WHERE builds.group_id = input_group_id
          AND builds.app_id = input_app_id
		  --filter by branch
		  AND (input_builds_branch IS NULL OR builds.branch = input_builds_branch)
		ORDER BY COALESCE(builds.committed_at, builds.created_at) DESC
		LIMIT input_builds_limit
	),
	BuildsCoverage AS (
		SELECT
            coverage.build_id,
            coverage.coverage_build_id,
            coverage.probes,
            coverage.methods_probes,
			coverage.total_probes,
			coverage.total_methods
        FROM raw_data.matview_builds_coverage_v3 coverage
        WHERE TRUE
          --Filters by coverage
          AND (input_coverage_in_other_builds IS TRUE OR coverage.build_id = coverage.coverage_build_id)
          AND (input_coverage_test_tag IS NULL OR input_coverage_test_tag = ANY(coverage.test_tags))
          AND (input_coverage_branch IS NULL OR coverage.branch = input_coverage_branch)
          AND (input_coverage_env_id IS NULL OR coverage.env_id = input_coverage_env_id)
	),
	BuildsCoverageComparedToBaseline AS (
		SELECT
            coverage.build_id,
            coverage.coverage_build_id,
            coverage.probes & ~baseline.probes AS probes,
            coverage.methods_probes & ~baseline.methods_probes AS method_probes,
            BIT_COUNT(~baseline.probes) AS total_probes,
            BIT_COUNT(~baseline.methods_probes) AS total_methods
        FROM BuildsCoverage coverage
		JOIN raw_data.matview_builds_comparison baseline ON baseline.build_id = coverage.build_id
		WHERE baseline.baseline_build_id = input_baseline_build_id
	),
    TargetBuildsCoverage AS (
        SELECT
            coverage.build_id,
            MAX(coverage.total_probes) AS total_probes,
            BIT_COUNT(BIT_OR(coverage.probes)) AS aggregated_covered_probes,
            BIT_COUNT(BIT_OR(CASE
                WHEN coverage.build_id = coverage.coverage_build_id
                THEN coverage.probes
                ELSE NULL
            END)) AS isolated_covered_probes,
            MAX(coverage.total_methods) AS total_changes,
            BIT_COUNT(BIT_OR(coverage.methods_probes)) AS aggregated_tested_changes,
            BIT_COUNT(BIT_OR(CASE
                WHEN coverage.build_id = coverage.coverage_build_id
                THEN coverage.methods_probes
                ELSE NULL
            END)) AS isolated_tested_changes
        FROM (
			SELECT * FROM BuildsCoverage WHERE input_baseline_build_id IS NULL
			UNION ALL
			SELECT * FROM BuildsCoverageComparedToBaseline WHERE input_baseline_build_id IS NOT NULL
		) coverage
        GROUP BY coverage.build_id
    )
    SELECT
        coverage.build_id::VARCHAR,
        builds.version_id::VARCHAR AS build_version,
        coverage.total_probes::INT,
        COALESCE(coverage.isolated_covered_probes, 0)::INT AS isolated_covered_probes,
        (coverage.total_probes - COALESCE(coverage.isolated_covered_probes, 0))::INT AS isolated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.isolated_covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) AS isolated_probes_coverage_ratio,
        COALESCE(coverage.aggregated_covered_probes, 0)::INT AS aggregated_covered_probes,
        (coverage.total_probes - COALESCE(coverage.aggregated_covered_probes, 0))::INT AS aggregated_missed_probes,
        COALESCE(CAST(COALESCE(coverage.aggregated_covered_probes, 0) AS FLOAT) / coverage.total_probes, 0.0) AS aggregated_probes_coverage_ratio,
        coverage.total_changes::INT,
		COALESCE(coverage.isolated_tested_changes, 0)::INT AS isolated_tested_changes,
		COALESCE(coverage.aggregated_tested_changes, 0)::INT AS aggregated_tested_changes
    FROM TargetBuildsCoverage coverage
    JOIN Builds builds ON builds.build_id = coverage.build_id
    ORDER BY builds.build_created_at ASC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get methods of a given build
-- @param input_build_id VARCHAR: The ID of the build
-- @param input_baseline_build_id VARCHAR DEFAULT NULL: The ID of the baseline build (optional)
-- @param input_package_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by package name (optional)
-- @param input_class_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by class name (optional)
-- @param input_method_name_pattern VARCHAR DEFAULT NULL: Pattern to filter by method name (optional)
-- @returns TABLE: A table containing methods
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS raw_data.get_methods_v2 CASCADE;

CREATE OR REPLACE FUNCTION raw_data.get_methods_v2(
    input_build_id VARCHAR,
    input_baseline_build_id VARCHAR DEFAULT NULL,
    input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    build_id VARCHAR,
	signature VARCHAR,
    classname VARCHAR,
    name VARCHAR,
    params VARCHAR,
    return_type VARCHAR,
    probes_count INT,
    change_type VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    WITH
    BaselineMethods AS (
    		SELECT
    			baseline.signature,
                baseline.body_checksum
    		FROM raw_data.matview_methods_with_rules baseline
    		WHERE baseline.build_id = input_baseline_build_id
    ),
    Methods AS (
        SELECT
			methods.build_id,
            methods.signature,
            methods.body_checksum,
            methods.probes_count,
            (CASE WHEN baseline.signature IS NULL THEN 'new' ELSE 'modified' END) AS change_type,
            builds.group_id,
            builds.app_id,
            builds.created_at AS build_created_at,
			methods.classname,
        	methods.name,
        	methods.params,
        	methods.return_type
        FROM raw_data.matview_methods_with_rules methods
        JOIN raw_data.builds builds ON builds.id = methods.build_id
        LEFT JOIN BaselineMethods baseline ON baseline.signature = methods.signature
        WHERE methods.build_id = input_build_id
            --filter by package pattern
            AND (input_package_name_pattern IS NULL OR methods.classname LIKE input_package_name_pattern)
            --filter by class pattern
            AND (input_class_name_pattern IS NULL OR methods.classname LIKE input_class_name_pattern)
            --filter by method pattern
            AND (input_method_name_pattern IS NULL OR methods.name LIKE input_method_name_pattern)
            --filter by baseline
            AND (input_baseline_build_id IS NULL OR baseline.signature IS NULL OR baseline.body_checksum <> methods.body_checksum)
    )
    SELECT
        methods.build_id::VARCHAR,
    	methods.signature::VARCHAR,
        methods.classname,
        methods.name,
        methods.params,
        methods.return_type,
        methods.probes_count::INT,
        methods.change_type::VARCHAR
    FROM Methods methods;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;