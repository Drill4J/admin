-----------------------------------------------------------------
-- Repeatable migration script to create functions for metrics
-- Migration version: v1
-- Compatible with: R__1_Data.sql v3
-----------------------------------------------------------------

-----------------------------------------------------------------
-- Function to get methods of a build
-- @param input_build_id: The ID of the build
-- @param input_package_name_pattern: Optional pattern to filter methods by package name
-- @param input_class_name_pattern: Optional pattern to filter methods by class name
-- @param input_method_name_pattern: Optional pattern to filter methods by method name
-- @returns TABLE: A table containing methods of the specified build
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS metrics.get_methods CASCADE;
CREATE OR REPLACE FUNCTION metrics.get_methods(
    input_build_id VARCHAR,
    input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    group_id VARCHAR,
    app_id VARCHAR,
    build_id VARCHAR,
    signature VARCHAR,
    class_name VARCHAR,
    method_name VARCHAR,
    method_params VARCHAR,
    return_type VARCHAR
) AS $$
DECLARE
    _group_id VARCHAR;
    _app_id VARCHAR;
BEGIN
	_group_id = split_part(input_build_id, ':', 1);
	_app_id = split_part(input_build_id, ':', 2);

	RETURN QUERY
    SELECT
        m.group_id::VARCHAR,
        m.app_id::VARCHAR,
        bm.build_id::VARCHAR,
        m.signature::VARCHAR,
        m.class_name::VARCHAR,
        m.method_name::VARCHAR,
        m.method_params::VARCHAR,
        m.return_type::VARCHAR AS return_type
    FROM metrics.build_methods bm
    JOIN metrics.methods m ON m.group_id = bm.group_id AND m.app_id = bm.app_id AND m.method_id = bm.method_id
    WHERE bm.group_id = _group_id
        AND bm.app_id = _app_id
        AND bm.build_id = input_build_id
        -- Filters by methods
        AND (input_package_name_pattern IS NULL OR m.class_name LIKE input_package_name_pattern)
        AND (input_class_name_pattern IS NULL OR m.class_name LIKE input_class_name_pattern)
        AND (input_method_name_pattern IS NULL OR m.method_name LIKE input_method_name_pattern)
	;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get methods with coverage for a build
-- @param input_build_id: The ID of the build
-- @param input_build_ids: Array of build IDs
-- @param input_test_session_id: Optional test session ID to filter coverage
-- @param input_test_launch_id: Optional test launch ID to filter coverage
-- @param input_baseline_build_id: Optional baseline build ID for comparison
-- @param input_package_name_pattern: Optional pattern to filter methods by package name
-- @param input_class_name_pattern: Optional pattern to filter methods by class name
-- @param input_method_name_pattern: Optional pattern to filter methods by method name
-- @param input_coverage_app_env_ids: Array of app environment IDs to filter coverage
-- @param input_coverage_branches: Array of branches to filter coverage
-- @param input_coverage_test_tags: Array of test tags to filter coverage
-- @param input_coverage_test_task_ids: Array of test task IDs to filter coverage
-- @param input_coverage_period_from: Optional timestamp to filter coverage by creation date
-- @param is_smart_coverage_before_build: Boolean value indicating whether smart coverage should only be considered up to the build date
-- @returns TABLE: A table containing methods with coverage information
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS metrics.get_methods_with_coverage CASCADE;
CREATE OR REPLACE FUNCTION metrics.get_methods_with_coverage(
    input_build_id VARCHAR DEFAULT NULL,
	input_build_ids VARCHAR[] DEFAULT NULL,
	input_test_session_id VARCHAR DEFAULT NULL,
	input_test_launch_id VARCHAR DEFAULT NULL,

    input_baseline_build_id VARCHAR DEFAULT NULL,
    input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL,

    input_coverage_app_env_ids VARCHAR[] DEFAULT NULL,
    input_coverage_branches VARCHAR[] DEFAULT NULL,
    input_coverage_test_tags VARCHAR[] DEFAULT NULL,
    input_coverage_test_task_ids VARCHAR[] DEFAULT NULL,
    input_coverage_period_from TIMESTAMP DEFAULT NULL,

    include_smart_coverage BOOLEAN DEFAULT TRUE,
	is_smart_coverage_before_build BOOLEAN DEFAULT FALSE
)
RETURNS TABLE (
    group_id VARCHAR,
    app_id VARCHAR,
    build_id VARCHAR,
    method_id VARCHAR,
	signature VARCHAR,
    class_name VARCHAR,
    method_name VARCHAR,
    method_params VARCHAR,
    return_type VARCHAR,
    probes_count INT,
    isolated_covered_probes INT,
    isolated_missed_probes INT,
    isolated_probes_coverage_ratio FLOAT,
   	aggregated_covered_probes INT,
    aggregated_missed_probes INT,
    aggregated_probes_coverage_ratio FLOAT
) AS $$
DECLARE
    _group_id VARCHAR;
    _app_id VARCHAR;
	_build_ids VARCHAR[];
BEGIN
	IF input_build_id IS null AND input_build_ids IS null THEN
        RAISE EXCEPTION 'One of the following parameters must be set: input_build_id, input_build_ids';
    END IF;
	_build_ids = CASE WHEN input_build_ids IS null THEN array[input_build_id] ELSE input_build_ids END;
	_group_id = split_part(_build_ids[1], ':', 1);
	_app_id = split_part(_build_ids[1], ':', 2);

    RETURN QUERY
    WITH
	methods_with_coverage AS (
		SELECT
			bm.group_id,
			bm.app_id,
			bm.build_id,
			bm.method_id,
			BIT_COUNT(BIT_OR(ic.probes)) AS isolated_covered_probes,
			BIT_COUNT(BIT_OR(COALESCE(sc.probes, REPEAT('0', m.probes_count)::VARBIT) | COALESCE(ic.probes, REPEAT('0', m.probes_count)::VARBIT))) AS covered_probes
		FROM metrics.build_methods bm
		JOIN metrics.builds b ON b.group_id = bm.group_id AND b.app_id = bm.app_id AND b.build_id = bm.build_id
		JOIN metrics.methods m ON m.group_id = bm.group_id AND m.app_id = bm.app_id AND m.method_id = bm.method_id
		LEFT JOIN metrics.build_methods baseline_bm ON baseline_bm.group_id = bm.group_id AND baseline_bm.app_id = bm.app_id
			AND baseline_bm.method_id = bm.method_id
			AND baseline_bm.build_id = input_baseline_build_id
		LEFT JOIN metrics.method_coverage ic ON  ic.group_id = bm.group_id AND ic.app_id = bm.app_id AND ic.method_id = bm.method_id
			AND ic.build_id = bm.build_id
			-- Filters by isolated coverage
			AND (input_test_session_id IS NULL OR ic.test_session_id = input_test_session_id)
			AND (input_test_launch_id IS NULL OR ic.test_launch_id = input_test_launch_id)
			AND (input_coverage_branches IS NULL OR ic.branch = ANY(input_coverage_branches::VARCHAR[]))
		  	AND (input_coverage_app_env_ids IS NULL OR ic.app_env_id = ANY(input_coverage_app_env_ids::VARCHAR[]))
		  	AND (input_coverage_test_tags IS NULL OR ic.test_tags && input_coverage_test_tags::VARCHAR[])
		  	AND (input_coverage_test_task_ids IS NULL OR ic.test_task_id = ANY(input_coverage_test_task_ids::VARCHAR[]))
		  	AND (input_coverage_period_from IS NULL OR ic.creation_day >= input_coverage_period_from)
		LEFT JOIN metrics.method_smartcoverage sc ON sc.group_id = bm.group_id AND sc.app_id = bm.app_id AND sc.method_id = bm.method_id
			-- Filters by smart coverage
			AND (include_smart_coverage IS true)
			AND (is_smart_coverage_before_build IS false OR sc.creation_day <= b.creation_day)
			AND (input_coverage_branches IS NULL OR sc.branch = ANY(input_coverage_branches::VARCHAR[]))
		  	AND (input_coverage_app_env_ids IS NULL OR sc.app_env_id = ANY(input_coverage_app_env_ids::VARCHAR[]))
		  	AND (input_coverage_test_tags IS NULL OR sc.test_tags && input_coverage_test_tags::VARCHAR[])
		  	AND (input_coverage_test_task_ids IS NULL OR sc.test_task_id = ANY(input_coverage_test_task_ids::VARCHAR[]))
		  	AND (input_coverage_period_from IS NULL OR sc.creation_day >= input_coverage_period_from)
		WHERE bm.group_id = _group_id
			AND bm.app_id = _app_id
			AND bm.build_id = ANY(_build_ids)
			-- Filters by methods
			AND (input_baseline_build_id IS NULL OR baseline_bm.method_id IS NULL)
			AND (input_package_name_pattern IS NULL OR m.class_name LIKE input_package_name_pattern)
			AND (input_class_name_pattern IS NULL OR m.class_name LIKE input_class_name_pattern)
			AND (input_method_name_pattern IS NULL OR m.method_name LIKE input_method_name_pattern)
		GROUP BY bm.group_id, bm.app_id, bm.method_id, bm.build_id
	)
    SELECT
        mc.group_id::VARCHAR,
        mc.app_id::VARCHAR,
        mc.build_id::VARCHAR,
        mc.method_id::VARCHAR,
    	m.signature::VARCHAR,
        m.class_name::VARCHAR,
        m.method_name::VARCHAR,
        m.method_params::VARCHAR,
        m.return_type::VARCHAR,
        m.probes_count::INT,

		COALESCE(mc.isolated_covered_probes, 0)::INT AS isolated_covered_probes,
        (m.probes_count - COALESCE(mc.isolated_covered_probes, 0))::INT AS isolated_missed_probes,
        COALESCE(CAST(COALESCE(mc.isolated_covered_probes, 0) AS FLOAT) / m.probes_count, 0.0) AS isolated_probes_coverage_ratio,

		COALESCE(mc.covered_probes, 0)::INT AS aggregated_covered_probes,
        (m.probes_count - COALESCE(mc.covered_probes, 0))::INT AS aggregated_missed_probes,
        COALESCE(CAST(COALESCE(mc.covered_probes, 0) AS FLOAT) / m.probes_count, 0.0) AS aggregated_probes_coverage_ratio
	FROM methods_with_coverage mc
	JOIN metrics.methods m ON m.group_id = mc.group_id AND m.app_id = mc.app_id AND m.method_id = mc.method_id
	;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get builds with coverage information
-- @param input_build_id: The ID of the build
-- @param input_build_ids: Array of build IDs
-- @param input_test_session_id: Optional test session ID to filter coverage
-- @param input_test_launch_id: Optional test launch ID to filter coverage
-- @param input_baseline_build_id: Optional baseline build ID for comparison
-- @param input_package_name_pattern: Optional pattern to filter methods by package name
-- @param input_class_name_pattern: Optional pattern to filter methods by class name
-- @param input_method_name_pattern: Optional pattern to filter methods by method name
-- @param input_coverage_app_env_ids: Array of app environment IDs to filter coverage
-- @param input_coverage_branches: Array of branches to filter coverage
-- @param input_coverage_test_tags: Array of test tags to filter coverage
-- @param input_coverage_test_task_ids: Array of test task IDs to filter coverage
-- @param input_coverage_period_from: Optional timestamp to filter coverage by creation date
-- @param is_smart_coverage_before_build: Boolean value indicating whether smart coverage should only be considered up to the build date
-- @returns TABLE: A table containing builds with coverage information
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS metrics.get_builds_with_coverage CASCADE;
CREATE OR REPLACE FUNCTION metrics.get_builds_with_coverage(
    input_build_id VARCHAR DEFAULT NULL,
    input_build_ids VARCHAR[] DEFAULT NULL,
    input_test_session_id VARCHAR DEFAULT NULL,
    input_test_launch_id VARCHAR DEFAULT NULL,

    input_baseline_build_id VARCHAR DEFAULT NULL,
    input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL,

    input_coverage_app_env_ids VARCHAR[] DEFAULT NULL,
    input_coverage_branches VARCHAR[] DEFAULT NULL,
    input_coverage_test_tags VARCHAR[] DEFAULT NULL,
    input_coverage_test_task_ids VARCHAR[] DEFAULT NULL,
    input_coverage_period_from TIMESTAMP DEFAULT NULL,

    include_smart_coverage BOOLEAN DEFAULT TRUE,
	is_smart_coverage_before_build BOOLEAN DEFAULT FALSE
)
RETURNS TABLE (
    group_id VARCHAR,
    app_id VARCHAR,
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
DECLARE
    _build_ids VARCHAR[];
BEGIN
	IF input_build_id IS null AND input_build_ids IS null THEN
        RAISE EXCEPTION 'One of the following parameters must be set: input_build_id, input_build_ids';
    END IF;
	_build_ids = CASE WHEN input_build_ids IS null THEN array[input_build_id] ELSE input_build_ids END;

    RETURN QUERY
	WITH
    build_coverage AS (
        SELECT
            c.group_id,
            c.app_id,
            c.build_id,

            COALESCE(SUM(c.probes_count), 0) AS total_probes,
            COALESCE(SUM(c.isolated_covered_probes), 0) AS isolated_covered_probes,
            COALESCE(SUM(c.aggregated_covered_probes), 0) AS aggregated_covered_probes,

            COUNT(*) AS total_methods,
            COUNT(CASE WHEN c.isolated_covered_probes > 0 THEN c.method_id END) AS isolated_tested_methods,
            COUNT(CASE WHEN c.aggregated_covered_probes > 0 THEN c.method_id END) AS aggregated_tested_methods
        FROM metrics.get_methods_with_coverage(
            input_build_ids => _build_ids,
            input_test_session_id => input_test_session_id,
            input_test_launch_id => input_test_launch_id,

            input_baseline_build_id => input_baseline_build_id,
            input_package_name_pattern => input_package_name_pattern,
            input_class_name_pattern => input_class_name_pattern,
            input_method_name_pattern => input_method_name_pattern,

            input_coverage_app_env_ids => input_coverage_app_env_ids,
            input_coverage_branches => input_coverage_branches,
            input_coverage_test_tags => input_coverage_test_tags,
            input_coverage_test_task_ids => input_coverage_test_task_ids,
            input_coverage_period_from => input_coverage_period_from,

            include_smart_coverage => include_smart_coverage,
            is_smart_coverage_before_build => is_smart_coverage_before_build
        ) c
        GROUP BY c.group_id, c.app_id, c.build_id
    )
	SELECT
		c.group_id::VARCHAR,
		c.app_id::VARCHAR,
		c.build_id::VARCHAR,

		c.total_probes::INT,
		c.isolated_covered_probes::INT,
		(c.total_probes - c.isolated_covered_probes)::INT AS isolated_missed_probes,
		COALESCE(c.isolated_covered_probes::FLOAT / COALESCE(c.total_probes, 0), 0.0)::FLOAT AS isolated_probes_coverage_ratio,
		c.aggregated_covered_probes::INT,
		(c.total_probes - c.aggregated_covered_probes)::INT AS aggregated_missed_probes,
		COALESCE(c.aggregated_covered_probes::FLOAT / COALESCE(c.total_probes, 0), 0.0)::FLOAT AS aggregated_probes_coverage_ratio,

		c.total_methods::INT,
		c.isolated_tested_methods::INT,
		(c.total_methods - c.isolated_tested_methods)::INT AS isolated_missed_methods,
		COALESCE(c.isolated_tested_methods::FLOAT / COALESCE(c.total_methods, 0), 0.0)::FLOAT AS isolated_methods_coverage_ratio,
		c.aggregated_tested_methods::INT,
		(c.total_methods - c.aggregated_tested_methods)::INT AS aggregated_missed_methods,
		COALESCE(c.aggregated_tested_methods::FLOAT / COALESCE(c.total_methods, 0), 0.0)::FLOAT AS aggregated_methods_coverage_ratio
	FROM build_coverage c
    ;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get changes in methods between two builds
-- @param input_build_id: The ID of the target build to compare
-- @param input_baseline_build_id: The ID of the baseline build for comparison
-- @param input_package_name_pattern: Optional pattern to filter methods by package name
-- @param input_class_name_pattern: Optional pattern to filter methods by class name
-- @param input_method_name_pattern: Optional pattern to filter methods by method name
-- @param include_equal: Boolean value indicating whether to include methods that are equal in both builds
-- @param include_deleted: Boolean value indicating whether to include methods that were deleted in the target build
-- @returns TABLE: A table containing changes in methods between the two builds
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS metrics.get_changes CASCADE;
CREATE OR REPLACE FUNCTION metrics.get_changes(
    input_build_id VARCHAR,
	input_baseline_build_id VARCHAR,

	input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL,

    include_equal BOOLEAN DEFAULT FALSE,
    include_deleted BOOLEAN DEFAULT FALSE
) RETURNS TABLE(
    group_id VARCHAR,
    app_id VARCHAR,
    signature VARCHAR,
    class_name VARCHAR,
    method_name VARCHAR,
    method_params VARCHAR,
    return_type VARCHAR,
    change_type VARCHAR
) AS $$
DECLARE
    _group_id VARCHAR;
    _app_id VARCHAR;
BEGIN
	_group_id = split_part(input_build_id, ':', 1);
	_app_id = split_part(input_build_id, ':', 2);

	RETURN QUERY
	WITH changes AS (
	    SELECT
            m.group_id,
            m.app_id,
            m.signature,
            MIN(m.class_name) AS class_name,
            MIN(m.method_name) AS method_name,
            MIN(m.method_params) AS method_params,
            MIN(m.return_type) AS return_type,
            MIN(bm.build_id) AS build_id,
            CASE
				WHEN COUNT(DISTINCT bm.method_id) > 1 THEN 'modified'
                WHEN COUNT(*) > 1 THEN 'equal'
                WHEN MIN(bm.build_id) = input_build_id THEN 'new'
                WHEN MIN(bm.build_id) = input_baseline_build_id THEN 'deleted'
                ELSE null
            END::VARCHAR AS change_type
        FROM metrics.build_methods bm
        JOIN metrics.methods m ON m.method_id = bm.method_id
        WHERE bm.group_id = _group_id
            AND bm.app_id = _app_id
            AND bm.build_id IN (input_baseline_build_id, input_build_id)
            -- Filters by methods
            AND (input_package_name_pattern IS NULL OR m.class_name LIKE input_package_name_pattern)
            AND (input_class_name_pattern IS NULL OR m.class_name LIKE input_class_name_pattern)
            AND (input_method_name_pattern IS NULL OR m.method_name LIKE input_method_name_pattern)
        GROUP BY m.group_id, m.app_id, m.signature
	)
	SELECT
        m.group_id::VARCHAR,
        m.app_id::VARCHAR,
        m.signature::VARCHAR,
        m.class_name::VARCHAR,
        m.method_name::VARCHAR,
        m.method_params::VARCHAR,
        m.return_type::VARCHAR,
        m.change_type::VARCHAR
    FROM changes m
    WHERE true
        -- Filters by changes
        AND (include_equal OR m.change_type <> 'equal')
        AND (include_deleted OR m.change_type <> 'deleted')
    ;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get changes in methods with coverage between two builds
-- @param input_build_id: The ID of the target build to compare
-- @param input_baseline_build_id: The ID of the baseline build for comparison
-- @param input_package_name_pattern: Optional pattern to filter methods by package name
-- @param input_class_name_pattern: Optional pattern to filter methods by class name
-- @param input_method_name_pattern: Optional pattern to filter methods by method name
-- @param input_coverage_app_env_ids: Array of app environment IDs to filter coverage
-- @param input_coverage_branches: Array of branches to filter coverage
-- @param input_coverage_test_tags: Array of test tags to filter coverage
-- @param input_coverage_test_task_ids: Array of test task IDs to filter coverage
-- @param input_coverage_period_from: Optional timestamp to filter coverage by creation date
-- @param is_smart_coverage_before_build: Boolean value indicating whether smart coverage should only be considered up to the build date
-- @returns TABLE: A table containing changes in methods with coverage information between the two builds
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS metrics.get_changes_with_coverage CASCADE;
CREATE OR REPLACE FUNCTION metrics.get_changes_with_coverage(
    input_build_id VARCHAR,
	input_baseline_build_id VARCHAR,

    input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL,

    input_coverage_app_env_ids VARCHAR[] DEFAULT NULL,
    input_coverage_branches VARCHAR[] DEFAULT NULL,
    input_coverage_test_tags VARCHAR[] DEFAULT NULL,
    input_coverage_test_task_ids VARCHAR[] DEFAULT NULL,
    input_coverage_period_from TIMESTAMP DEFAULT NULL,

    include_smart_coverage BOOLEAN DEFAULT TRUE,
    is_smart_coverage_before_build BOOLEAN DEFAULT FALSE
)
RETURNS TABLE (
    group_id VARCHAR,
    app_id VARCHAR,
    signature VARCHAR,
    class_name VARCHAR,
    method_name VARCHAR,
    method_params VARCHAR,
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
    SELECT
        m.group_id::VARCHAR,
        m.app_id::VARCHAR,
        m.signature::VARCHAR,
        m.class_name::VARCHAR,
        m.method_name::VARCHAR,
        m.method_params::VARCHAR,
        m.return_type::VARCHAR,
        c.probes_count::INT,

        c.isolated_covered_probes::INT,
        c.isolated_missed_probes::INT,
        c.isolated_probes_coverage_ratio::FLOAT,

        c.aggregated_covered_probes::INT,
        c.aggregated_missed_probes::INT,
        c.aggregated_probes_coverage_ratio::FLOAT,

        m.change_type::VARCHAR
    FROM metrics.get_changes(
    	input_build_id => input_build_id,
    	input_baseline_build_id => input_baseline_build_id,
    	input_package_name_pattern => input_package_name_pattern,
    	input_class_name_pattern => input_class_name_pattern,
    	input_method_name_pattern => input_method_name_pattern,
    	include_deleted => false,
    	include_equal => false
    ) m
    JOIN metrics.get_methods_with_coverage(
    	input_build_id => input_build_id,
        input_baseline_build_id => input_baseline_build_id,
        input_package_name_pattern => input_package_name_pattern,
        input_class_name_pattern => input_class_name_pattern,
        input_method_name_pattern => input_method_name_pattern,

        input_coverage_app_env_ids => input_coverage_app_env_ids,
        input_coverage_branches => input_coverage_branches,
        input_coverage_test_tags => input_coverage_test_tags,
        input_coverage_test_task_ids => input_coverage_test_task_ids,
        input_coverage_period_from => input_coverage_period_from,

        include_smart_coverage => include_smart_coverage,
        is_smart_coverage_before_build => is_smart_coverage_before_build
    ) c ON c.signature = m.signature
    ;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get impacted tests based on method changes
-- @param input_build_id: The ID of the target build to analyze
-- @param input_baseline_build_id: The ID of the baseline build for comparison
-- @param input_package_name_pattern: Optional pattern to filter methods by package name
-- @param input_class_name_pattern: Optional pattern to filter methods by class name
-- @param input_method_name_pattern: Optional pattern to filter methods by method name
-- @param input_test_task_ids: Array of test task IDs to filter tests
-- @param input_test_tags: Array of test tags to filter tests
-- @param input_test_path_pattern: Optional pattern to filter tests by path
-- @param input_test_name_pattern: Optional pattern to filter tests by name
-- @returns TABLE: A table containing impacted tests and their methods based on changes in the specified builds
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS metrics.get_impacted_tests CASCADE;
CREATE OR REPLACE FUNCTION metrics.get_impacted_tests(
    input_build_id VARCHAR,
	input_baseline_build_id VARCHAR,

	input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL,

	input_test_task_ids VARCHAR[] DEFAULT NULL,
    input_test_tags VARCHAR[] DEFAULT NULL,
    input_test_path_pattern VARCHAR DEFAULT NULL,
    input_test_name_pattern VARCHAR DEFAULT NULL
) RETURNS TABLE(
    group_id VARCHAR,
    test_definition_id VARCHAR,
    test_path VARCHAR,
    test_name VARCHAR,
    impacted_methods JSON
) AS $$
DECLARE
    _group_id VARCHAR;
    _app_id VARCHAR;
BEGIN
	_group_id = split_part(input_build_id, ':', 1);
	_app_id = split_part(input_build_id, ':', 2);

	RETURN QUERY
    WITH
	changes AS (
	    SELECT
	        m.group_id,
            m.app_id,
            m.signature,
            m.class_name,
            m.method_name,
            m.method_params,
            m.return_type,
            m.change_type
        FROM metrics.get_changes(
            input_build_id => input_build_id,
            input_baseline_build_id => input_baseline_build_id,
            input_package_name_pattern => input_package_name_pattern,
            input_class_name_pattern => input_class_name_pattern,
            input_method_name_pattern => input_method_name_pattern,
            include_deleted => true,
            include_equal => false
        ) m
    ),
    impacted_methods AS (
        SELECT DISTINCT
            c.test_definition_id,
            m.group_id,
            m.app_id,
            m.signature,
            m.class_name,
            m.method_name,
            m.method_params,
            m.return_type
        FROM metrics.method_coverage c
        JOIN metrics.methods m ON m.group_id = c.group_id AND m.app_id = c.app_id AND m.method_id = c.method_id
        JOIN changes changed_m ON changed_m.group_id = m.group_id AND changed_m.app_id = m.app_id AND changed_m.signature = m.signature
        WHERE true
            -- Filters by tests
            AND (input_test_task_ids IS NULL OR c.test_task_id = ANY(input_test_task_ids::VARCHAR[]))
            AND (input_test_tags IS NULL OR c.test_tags && input_test_tags::VARCHAR[])
            AND (input_test_path_pattern IS NULL OR c.test_path LIKE input_test_path_pattern)
            AND (input_test_name_pattern IS NULL OR c.test_name LIKE input_test_name_pattern)
    ),
    impacted_tests AS (
        SELECT
            im.test_definition_id,
            json_agg(
                json_build_object(
                    'class_name', im.class_name,
                    'method_name', im.method_name,
                    'method_params', im.method_params,
                    'return_type', im.return_type
                ) ORDER BY im.signature
            ) AS impacted_methods
        FROM impacted_methods im
        GROUP BY im.test_definition_id
    )
    SELECT
        td.group_id,
        td.test_definition_id,
        td.test_path,
        td.test_name,
        it.impacted_methods AS impacted_methods
    FROM metrics.test_definitions td
	JOIN impacted_tests it ON it.test_definition_id = td.test_definition_id
    WHERE td.group_id = _group_id
	;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get similar builds based on method changes
-- @param input_build_id: The ID of the target build to analyze
-- @param input_branches: Optional array of branches to filter builds
-- @returns TABLE: A table containing similar builds based on method changes
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS metrics.get_similar_builds CASCADE;
CREATE OR REPLACE FUNCTION metrics.get_similar_builds(
    input_build_id VARCHAR DEFAULT NULL,
    input_branches VARCHAR[] DEFAULT NULL
)
RETURNS TABLE (
    group_id VARCHAR,
    app_id VARCHAR,
    build_id VARCHAR,
	target_total_methods BIGINT,
	target_equal_methods BIGINT,
	identity_ratio FLOAT
) AS $$
DECLARE
    _group_id VARCHAR;
    _app_id VARCHAR;
BEGIN
	_group_id = split_part(input_build_id, ':', 1);
	_app_id = split_part(input_build_id, ':', 2);

	RETURN QUERY
	WITH
	    target_build AS (
            SELECT
                COUNT(*) AS target_total_methods
            FROM metrics.build_methods bm
            WHERE bm.group_id = _group_id
                AND bm.app_id = _app_id
                AND bm.build_id = input_build_id
            GROUP BY bm.group_id, bm.app_id, bm.build_id
        ),
	    builds AS (
	        SELECT
                bm.group_id::VARCHAR,
                bm.app_id::VARCHAR,
                bm.build_id::VARCHAR,
                COUNT(*) AS target_equal_methods
            FROM metrics.build_methods bm
            JOIN metrics.builds b ON b.group_id = bm.group_id AND b.app_id = bm.app_id AND b.build_id = bm.build_id
            JOIN metrics.build_methods tm ON tm.group_id = bm.group_id AND tm.app_id = bm.app_id AND tm.method_id = bm.method_id
            WHERE bm.group_id = _group_id
                AND bm.app_id = _app_id
                AND tm.build_id = input_build_id
                AND bm.build_id <> input_build_id
                -- Filters by builds
                AND (input_branches IS NULL OR b.branch = ANY(input_branches::VARCHAR[]))
            GROUP BY bm.group_id, bm.app_id, bm.build_id
	    )
	SELECT
        b.group_id,
        b.app_id,
        b.build_id,
        tb.target_total_methods,
        b.target_equal_methods,
        COALESCE(CAST(b.target_equal_methods AS FLOAT) / NULLIF(tb.target_total_methods, 0), 0.0) AS identity_ratio
    FROM builds b, target_build tb
	;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get recommended tests based on running previous tests on similar builds
-- @param input_build_id: The ID of the build to analyze
-- @param input_package_name_pattern: Optional pattern to filter methods by package name
-- @param input_class_name_pattern: Optional pattern to filter methods by class name
-- @param input_method_name_pattern: Optional pattern to filter methods by method name
-- @param input_test_task_ids: Array of test task IDs to filter tests
-- @param input_test_tags: Array of test tags to filter tests
-- @param input_test_path_pattern: Optional pattern to filter tests by path
-- @param input_test_name_pattern: Optional pattern to filter tests by name
-- @param input_coverage_app_env_ids: Array of app environment IDs to filter coverage
-- @param input_coverage_branches: Array of branches to filter coverage
-- @param input_coverage_period_from: Optional timestamp to filter coverage by creation date
-- @param tests_to_skip: A Boolean value indicating whether to return tests that should be skipped (true) or whether to return tests that should be run (false).
-- @returns TABLE: A table containing recommended tests based on method changes and coverage
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS metrics.get_recommended_tests CASCADE;
CREATE OR REPLACE FUNCTION metrics.get_recommended_tests(
    input_build_id VARCHAR,

	input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL,

	input_test_task_ids VARCHAR[] DEFAULT NULL,
	input_test_tags VARCHAR[] DEFAULT NULL,
	input_test_path_pattern VARCHAR DEFAULT NULL,
	input_test_name_pattern VARCHAR DEFAULT NULL,

	input_coverage_app_env_ids VARCHAR[] DEFAULT NULL,
    input_coverage_branches VARCHAR[] DEFAULT NULL,
    input_coverage_period_from TIMESTAMP DEFAULT NULL,

	tests_to_skip BOOLEAN DEFAULT FALSE
) RETURNS TABLE(
    group_id VARCHAR,
    test_definition_id VARCHAR,
    test_path VARCHAR,
    test_name VARCHAR,
    test_metadata JSON NULL
) AS $$
DECLARE
    _group_id VARCHAR;
    _app_id VARCHAR;
BEGIN
	_group_id = split_part(input_build_id, ':', 1);
	_app_id = split_part(input_build_id, ':', 2);

	RETURN QUERY
    WITH
    target_methods AS (
        SELECT
            m.group_id,
            m.app_id,
            m.method_id
        FROM metrics.build_methods bm
		JOIN metrics.methods m ON m.group_id = bm.group_id AND m.app_id = bm.app_id AND m.method_id = bm.method_id
        WHERE bm.group_id = _group_id
            AND bm.app_id = _app_id
            AND bm.build_id = input_build_id
            -- Filters by methods
            AND (input_package_name_pattern IS NULL OR m.class_name LIKE input_package_name_pattern)
            AND (input_class_name_pattern IS NULL OR m.class_name LIKE input_class_name_pattern)
            AND (input_method_name_pattern IS NULL OR m.method_name LIKE input_method_name_pattern)
    ),
    test_launches_with_coverage AS (
        SELECT
            c.group_id,
            c.app_id,
            c.test_definition_id,
            c.test_launch_id,
            BOOL_OR(CASE WHEN tm.method_id IS NULL THEN true ELSE false END) AS has_changed_methods
        FROM metrics.method_coverage c
        JOIN metrics.methods m ON m.group_id = c.group_id AND m.app_id = c.app_id AND m.method_id = c.method_id
        LEFT JOIN target_methods tm ON tm.group_id = c.group_id AND tm.app_id = c.app_id AND tm.method_id = c.method_id
        WHERE c.group_id = _group_id
            AND c.app_id = _app_id
            AND c.test_launch_id IS NOT NULL
            AND c.test_result = 'PASSED'
            -- Filters by methods
            AND (input_package_name_pattern IS NULL OR m.class_name LIKE input_package_name_pattern)
            AND (input_class_name_pattern IS NULL OR m.class_name LIKE input_class_name_pattern)
            AND (input_method_name_pattern IS NULL OR m.method_name LIKE input_method_name_pattern)
            -- Filters by tests
            AND (input_test_task_ids IS NULL OR c.test_task_id = ANY(input_test_task_ids::VARCHAR[]))
            AND (input_test_tags IS NULL OR c.test_tags && input_test_tags::VARCHAR[])
            AND (input_test_path_pattern IS NULL OR c.test_path LIKE input_test_path_pattern)
            AND (input_test_name_pattern IS NULL OR c.test_name LIKE input_test_name_pattern)
            -- Filters by coverage
            AND (input_coverage_app_env_ids IS NULL OR c.app_env_id = ANY(input_coverage_app_env_ids::VARCHAR[]))
            AND (input_coverage_branches IS NULL OR c.branch = ANY(input_coverage_branches::VARCHAR[]))
            AND (input_coverage_period_from IS NULL OR c.creation_day >= input_coverage_period_from)
        GROUP BY c.group_id, c.app_id, c.test_definition_id, c.test_launch_id
    ),
    recommended_tests AS (
        SELECT
            tlc.group_id,
            tlc.test_definition_id
        FROM test_launches_with_coverage tlc
        GROUP BY tlc.group_id, tlc.test_definition_id
        HAVING BOOL_AND(tlc.has_changed_methods) = NOT tests_to_skip
    )
	SELECT
	    td.group_id,
	    td.test_definition_id,
        td.test_path,
        td.test_name,
        td.test_metadata
    FROM metrics.test_definitions td
	JOIN recommended_tests rt ON rt.test_definition_id = td.test_definition_id
    WHERE td.group_id = _group_id
	;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;

-----------------------------------------------------------------
-- Function to get impacted methods of impacted tests based on method changes and test executions
-- @param input_build_id: The ID of the target build to analyze
-- @param input_baseline_build_id: The ID of the baseline build for comparison
-- @param input_package_name_pattern: Optional pattern to filter methods by package name
-- @param input_class_name_pattern: Optional pattern to filter methods by class name
-- @param input_method_name_pattern: Optional pattern to filter methods by method name
-- @param input_test_task_ids: Array of test task IDs to filter tests
-- @param input_test_tags: Array of test tags to filter tests
-- @param input_test_path_pattern: Optional pattern to filter tests by path
-- @param input_test_name_pattern: Optional pattern to filter tests by name
-- @returns TABLE: A table containing impacted tests and their methods based on changes in the specified builds
-----------------------------------------------------------------
DROP FUNCTION IF EXISTS metrics.get_impacted_methods CASCADE;
CREATE OR REPLACE FUNCTION metrics.get_impacted_methods(
    input_build_id VARCHAR,
	input_baseline_build_id VARCHAR,

	input_package_name_pattern VARCHAR DEFAULT NULL,
    input_class_name_pattern VARCHAR DEFAULT NULL,
    input_method_name_pattern VARCHAR DEFAULT NULL,

	input_test_task_ids VARCHAR[] DEFAULT NULL,
    input_test_tags VARCHAR[] DEFAULT NULL,
    input_test_path_pattern VARCHAR DEFAULT NULL,
    input_test_name_pattern VARCHAR DEFAULT NULL
) RETURNS TABLE(
    group_id VARCHAR,
    app_id VARCHAR,
    class_name VARCHAR,
    method_name VARCHAR,
    method_params VARCHAR,
    return_type VARCHAR
) AS $$
DECLARE
    _group_id VARCHAR;
    _app_id VARCHAR;
BEGIN
	_group_id = split_part(input_build_id, ':', 1);
	_app_id = split_part(input_build_id, ':', 2);

	RETURN QUERY
    WITH
	changes AS (
	    SELECT
	        m.group_id,
            m.app_id,
            m.signature,
            m.class_name,
            m.method_name,
            m.method_params,
            m.return_type,
            m.change_type
        FROM metrics.get_changes(
            input_build_id => input_build_id,
            input_baseline_build_id => input_baseline_build_id,
            input_package_name_pattern => input_package_name_pattern,
            input_class_name_pattern => input_class_name_pattern,
            input_method_name_pattern => input_method_name_pattern,
            include_deleted => true,
            include_equal => false
        ) m
    )
    SELECT DISTINCT
        m.group_id::VARCHAR,
        m.app_id::VARCHAR,
        m.class_name::VARCHAR,
        m.method_name::VARCHAR,
        m.method_params::VARCHAR,
        m.return_type::VARCHAR
    FROM metrics.method_coverage c
    JOIN metrics.methods m ON m.group_id = c.group_id AND m.app_id = c.app_id AND m.method_id = c.method_id
    JOIN changes changed_m ON changed_m.group_id = m.group_id AND changed_m.app_id = m.app_id AND changed_m.signature = m.signature
    WHERE true
        -- Filters by tests
        AND (input_test_task_ids IS NULL OR c.test_task_id = ANY(input_test_task_ids::VARCHAR[]))
        AND (input_test_tags IS NULL OR c.test_tags && input_test_tags::VARCHAR[])
        AND (input_test_path_pattern IS NULL OR c.test_path LIKE input_test_path_pattern)
        AND (input_test_name_pattern IS NULL OR c.test_name LIKE input_test_name_pattern)
    ;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;