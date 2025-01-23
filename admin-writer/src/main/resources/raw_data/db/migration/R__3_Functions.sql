-----------------------------------------------------------------
-- Delete all functions in raw_data schema
-----------------------------------------------------------------
DO
$$
DECLARE
    function_name TEXT;
BEGIN
    FOR function_name IN
        SELECT routine_name
        FROM information_schema.routines
        WHERE routine_schema = 'raw_data' AND routine_type = 'FUNCTION'
    LOOP
        EXECUTE 'DROP FUNCTION raw_data.' || function_name || ' CASCADE';
    END LOOP;
END;
$$;
-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_coverage_total_percent(
    _build_id VARCHAR
)
RETURNS FLOAT AS $$
BEGIN
RETURN (
    WITH
    InstanceIds AS (
        SELECT * FROM raw_data.get_instance_ids(_build_id)
    ),
    Methods AS (
        SELECT * FROM raw_data.get_methods(_build_id)
    ),
    Classes AS (
        SELECT
            classname,
            SUM(Methods.probes_count) as probes_count
        FROM Methods
        GROUP BY classname
    ),
    ClassesCoverage AS (
        SELECT
            classname,
            BIT_COUNT(BIT_OR(probes)) AS covered_probes_count
        FROM raw_data.coverage coverage
        JOIN InstanceIds ON coverage.instance_id = InstanceIds.__id
        GROUP BY coverage.classname
    )
    SELECT
        COALESCE(SUM(ClassesCoverage.covered_probes_count) / SUM(Classes.probes_count), 0) as total_coverage
    FROM Classes
    LEFT JOIN ClassesCoverage ON Classes.classname = ClassesCoverage.classname
    );
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_classes(
    _build_id VARCHAR
)
RETURNS TABLE (
    _classname varchar,
    _merged_probes bit
) AS $$
BEGIN
    RETURN QUERY
    WITH
    InstanceIds AS (
        SELECT * FROM raw_data.get_instance_ids(_build_id)
    ),
    Classnames AS (
        SELECT classname
        -- !warning! one cannot simply do SUM(methods.probes_count) to get class probe count - bc it'll aggregate dup entries from different instances
        FROM raw_data.get_methods(_build_id)
        --  AND methods.classname LIKE CONCAT({{package_filter}, '%') -- filter by package name
        GROUP BY classname
    ),
    Coverage AS (
        SELECT
            coverage.classname,
            BIT_OR(coverage.probes) AS merged_probes
        FROM raw_data.coverage coverage
        JOIN InstanceIds ON coverage.instance_id = InstanceIds.__id
        GROUP BY coverage.classname
    )
    SELECT
        Classnames.classname,
        Coverage.merged_probes
    FROM Classnames
    LEFT JOIN Coverage ON Classnames.classname = Coverage.classname;
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_test_launch_ids(
    input_group_id VARCHAR,
	test_definition_ids VARCHAR[] DEFAULT NULL,
    test_names VARCHAR[] DEFAULT NULL,
    test_results VARCHAR[] DEFAULT NULL,
    test_runners VARCHAR[] DEFAULT NULL
)
RETURNS TABLE (
    __id VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT DISTINCT launches.id as test_launch_id
    FROM raw_data.test_definitions definitions
    JOIN raw_data.test_launches launches ON launches.test_definition_id = definitions.id
    WHERE definitions.group_id = input_group_id
        AND launches.group_id = input_group_id
        AND (test_definition_ids IS NULL OR definitions.id = ANY(test_definition_ids))
        AND (test_names IS NULL OR definitions.name = ANY(test_names))
        AND (test_runners IS NULL OR definitions.runner = ANY(test_runners))
        AND (test_results IS NULL OR launches.result = ANY(test_results))
    ;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE;


-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_methods_list(
    input_build_id VARCHAR,

	methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

    -- TODO filter by coverage dates
	coverage_created_at_start TIMESTAMP DEFAULT NULL,
    coverage_created_at_end TIMESTAMP DEFAULT NULL,

    test_task_ids VARCHAR[] DEFAULT NULL,
    test_names VARCHAR[] DEFAULT NULL,
    test_results VARCHAR[] DEFAULT NULL,
    test_runners VARCHAR[] DEFAULT NULL
)
RETURNS TABLE (
    __classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
	__body_checksum VARCHAR,
    __probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
WITH
Methods AS (
    SELECT *
    FROM raw_data.get_methods(
        input_build_id,
        -- TODO filter by class & method name
        methods_class_name_pattern,
       	methods_method_name_pattern
    )
),
Coverage AS (
	SELECT
		c.classname,
		c.instance_id,
		BIT_OR(c.probes) as probes
	FROM raw_data.coverage c
    -- TODO filter by env
	WHERE c.instance_id IN (SELECT __id FROM raw_data.get_instance_ids(input_build_id))
		AND (
		    (c.test_id IN (
                SELECT DISTINCT launches.id AS test_launch_id
                FROM raw_data.test_definitions definitions
                JOIN raw_data.test_launches launches
                    ON launches.test_definition_id = definitions.id
                JOIN raw_data.test_sessions sessions
                    ON launches.test_session_id = sessions.id
                WHERE
                    definitions.group_id = split_part(input_build_id, ':', 1)
                    -- TODO checking launches & sessions might be unnecessary if ids are guaranteed to be 100% unique
                    AND launches.group_id = split_part(input_build_id, ':', 1)
                    AND sessions.group_id = split_part(input_build_id, ':', 1)
                    -- TODO add params to filter by test attributes
                     AND (test_task_ids is NULL OR sessions.test_task_id = ANY (test_task_ids))
                     AND (test_names IS NULL OR definitions.name = ANY(test_names))
                     AND (test_runners IS NULL OR definitions.runner = ANY(test_runners))
                     AND (test_results IS NULL OR launches.result = ANY(test_results))
                ))
            -- include coverage w/o test context only if there are no test filters applied
            OR (test_task_ids IS NULL AND
                test_names IS NULL AND
                test_runners IS NULL AND
                test_results IS NULL AND
                c.test_id = 'TEST_CONTEXT_NONE'
            )
        )
		GROUP BY
			c.classname,
			c.instance_id
),
MethodsCoverage AS (
	SELECT
		Methods.signature,
		Methods.probes_count,
		SUBSTRING(Coverage.probes FROM Methods.probe_start_pos + 1 FOR Methods.probes_count) AS substring_probes,
	 	BIT_LENGTH(SUBSTRING(Coverage.probes FROM Methods.probe_start_pos + 1 FOR Methods.probes_count)) AS substring_probes_length
	FROM Methods
	LEFT JOIN Coverage ON Methods.classname = Coverage.classname
),
MergedCoverage AS (
	SELECT
		MethodsCoverage.signature,
		MethodsCoverage.substring_probes_length,
		MethodsCoverage.probes_count,
		CAST(BIT_COUNT(BIT_OR(MethodsCoverage.substring_probes)) AS INT) AS covered_probes
	FROM MethodsCoverage
	GROUP BY
		MethodsCoverage.signature,
		MethodsCoverage.substring_probes_length,
		MethodsCoverage.probes_count
)
SELECT
	Methods.classname,
	Methods.name,
	Methods.params,
	Methods.return_type,
	Methods.body_checksum,
	MergedCoverage.probes_count,
	COALESCE(MergedCoverage.covered_probes, 0) AS covered_probes,
	MergedCoverage.probes_count - COALESCE(MergedCoverage.covered_probes, 0) AS missed_probes,
	COALESCE(CAST(MergedCoverage.covered_probes AS FLOAT) / MergedCoverage.probes_count, 0) AS probes_coverage_ratio
FROM Methods
LEFT JOIN MergedCoverage ON MergedCoverage.signature = Methods.signature
WHERE Methods.build_id = input_build_id
	AND Methods.probes_count > 0
ORDER BY
	MergedCoverage.probes_count - COALESCE(MergedCoverage.covered_probes, 0) DESC
;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 5000; -- indicate that function is expensive
--TODO -- ROWS 5000; -- can also adjust result set expectations

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_aggregate_coverage_by_methods_list(
    input_build_id VARCHAR,

	methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

    -- TODO filter by coverage dates
	coverage_created_at_start TIMESTAMP DEFAULT NULL,
    coverage_created_at_end TIMESTAMP DEFAULT NULL,

    test_task_ids VARCHAR[] DEFAULT NULL,
    test_names VARCHAR[] DEFAULT NULL,
    test_results VARCHAR[] DEFAULT NULL,
    test_runners VARCHAR[] DEFAULT NULL
)
RETURNS TABLE (
    __classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
	__body_checksum VARCHAR,
    __probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
WITH
Methods AS (
    SELECT *
    FROM raw_data.get_methods(input_build_id, methods_class_name_pattern, methods_method_name_pattern)
),
MatchingMethods AS (
    WITH
    SameGroupAndAppMethods AS (
        SELECT *
        FROM raw_data.get_same_group_and_app_methods(input_build_id, methods_class_name_pattern, methods_method_name_pattern)
    )
    SELECT
        Methods.signature,
        Methods.body_checksum,
        Methods.classname,
        Methods.probes_count,
        Methods.probe_start_pos,
		same_methods.build_id
    FROM SameGroupAndAppMethods same_methods
    JOIN Methods ON
        Methods.body_checksum = same_methods.body_checksum
        AND Methods.signature = same_methods.signature
		AND Methods.probes_count = same_methods.probes_count
    ORDER BY Methods.body_checksum
),
MatchingInstances AS (
    SELECT
        MatchingMethods.*,
        instances.id as instance_id
    FROM raw_data.instances instances
    JOIN MatchingMethods ON
        MatchingMethods.build_id = instances.build_id
),
Coverage AS (
	SELECT
		c.classname,
		c.instance_id,
		BIT_OR(c.probes) as probes
	FROM raw_data.coverage c
    -- TODO filter by env
	WHERE c.instance_id IN (SELECT distinct(instance_id) FROM MatchingInstances)
		AND (
		    (c.test_id IN (
                SELECT DISTINCT launches.id AS test_launch_id
                FROM raw_data.test_definitions definitions
                JOIN raw_data.test_launches launches
                    ON launches.test_definition_id = definitions.id
                JOIN raw_data.test_sessions sessions
                    ON launches.test_session_id = sessions.id
                WHERE
                    definitions.group_id = split_part(input_build_id, ':', 1)
                    -- TODO checking launches & sessions might be unnecessary if ids are guaranteed to be 100% unique
                    AND launches.group_id = split_part(input_build_id, ':', 1)
                    AND sessions.group_id = split_part(input_build_id, ':', 1)
                    -- TODO add params to filter by test attributes
                     AND (test_task_ids is NULL OR sessions.test_task_id = ANY (test_task_ids))
                     AND (test_names IS NULL OR definitions.name = ANY(test_names))
                     AND (test_runners IS NULL OR definitions.runner = ANY(test_runners))
                     AND (test_results IS NULL OR launches.result = ANY(test_results))
                ))
			OR (test_task_ids IS NULL AND
                test_names IS NULL AND
                test_runners IS NULL AND
                test_results IS NULL AND
                c.test_id = 'TEST_CONTEXT_NONE'
            )
        )
	GROUP BY
		c.classname,
		c.instance_id
),
MethodsCoverage AS (
	SELECT
		MatchingInstances.signature,
		MatchingInstances.body_checksum,
		MatchingInstances.probes_count,
		ARRAY_AGG(MatchingInstances.build_id) AS source_build_ids,
		BIT_OR(SUBSTRING(coverage.probes FROM MatchingInstances.probe_start_pos + 1 FOR MatchingInstances.probes_count)) AS probes,
		BIT_COUNT(BIT_OR(SUBSTRING(coverage.probes FROM MatchingInstances.probe_start_pos + 1 FOR MatchingInstances.probes_count))) AS covered_probes
	FROM Coverage
	JOIN MatchingInstances ON MatchingInstances.instance_id = coverage.instance_id AND MatchingInstances.classname = coverage.classname
	GROUP BY
		MatchingInstances.signature,
		MatchingInstances.body_checksum,
		MatchingInstances.probes_count,
		BIT_LENGTH(SUBSTRING(coverage.probes FROM MatchingInstances.probe_start_pos + 1 FOR MatchingInstances.probes_count))
),
MethodsCoverage2 AS (
	SELECT
		Methods.classname,
		Methods.name,
		Methods.params,
		Methods.return_type,
		Methods.body_checksum,
		Methods.probes_count,
		-- pick highest number of covered probes. Variability happens in cases when the same signature & body_checksum yield different number of probes
		MAX(COALESCE(CAST(MethodsCoverage.covered_probes AS INT), 0)) AS covered_probes
	FROM Methods
	LEFT JOIN MethodsCoverage ON Methods.signature = MethodsCoverage.signature AND Methods.body_checksum = MethodsCoverage.body_checksum
	GROUP BY
		Methods.classname,
		Methods.name,
		Methods.params,
		Methods.return_type,
		Methods.body_checksum,
		Methods.probes_count
)
SELECT
	*,
	MethodsCoverage2.probes_count - COALESCE(MethodsCoverage2.covered_probes, 0) AS missed_probes,
	COALESCE(CAST(MethodsCoverage2.covered_probes AS FLOAT) / MethodsCoverage2.probes_count, 0.0) AS probes_coverage_ratio
FROM MethodsCoverage2
ORDER BY MethodsCoverage2.probes_count - COALESCE(MethodsCoverage2.covered_probes, 0) DESC
;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 7000; -- indicate that function is expensive
--TODO -- ROWS 5000; -- can also adjust result set expectations

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_packages(
    _build_id VARCHAR
)
RETURNS TABLE(package_name VARCHAR, coverage_percentage FLOAT) AS $$
BEGIN
    RETURN QUERY
    WITH
    InstanceIds AS (
        SELECT * FROM raw_data.get_instance_ids(_build_id)
    ),
    Methods AS (
        SELECT * FROM raw_data.get_methods(_build_id)
    ),
    Classes AS (
        SELECT
            classname,
            SUM(Methods.probes_count) as probes_count
        FROM Methods
        GROUP BY classname
    ),
    Packages AS (
        SELECT
            REVERSE(
                SUBSTRING(
                    REVERSE(Classes.classname) FROM POSITION('/' IN REVERSE(Classes.classname)) + 1)) package_name,
            SUM(Classes.probes_count) as package_probes_count
        FROM Classes
        GROUP BY package_name
    ),
    ClassesCoverage AS (
        SELECT
            REVERSE(
                SUBSTRING(
                    REVERSE(classname) FROM POSITION('/' IN REVERSE(classname)) + 1)) AS package_name,
            classname,
            BIT_COUNT(BIT_OR(probes)) AS covered_probes_count
        FROM
            raw_data.coverage coverage
        JOIN InstanceIds ON coverage.instance_id = InstanceIds.__id
        GROUP BY
            classname, package_name
    )
    SELECT
        CAST(Packages.package_name AS VARCHAR) as package_name,
        CAST(COALESCE(
            (SUM(ClassesCoverage.covered_probes_count) / Packages.package_probes_count) * 100.0
        , 0) AS FLOAT) AS coverage_percentage
    FROM Packages
    LEFT JOIN ClassesCoverage ON Packages.package_name = ClassesCoverage.package_name
    GROUP BY Packages.package_name,
        Packages.package_probes_count
    ORDER BY
        coverage_percentage DESC;

END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_aggregate_coverage_by_risks(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR,

    methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL

-- TODO add filtering by tests & coverage
--    test_definition_ids VARCHAR[] DEFAULT NULL,
--    test_names VARCHAR[] DEFAULT NULL,
--    test_results VARCHAR[] DEFAULT NULL,
--    test_runners VARCHAR[] DEFAULT NULL,
--
--    coverage_created_at_start TIMESTAMP DEFAULT NULL,
--    coverage_created_at_end TIMESTAMP DEFAULT NULL
) RETURNS TABLE (
    __risk_type TEXT,
    __build_id VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
    __classname VARCHAR,
    __body_checksum VARCHAR,
    __signature VARCHAR,
    __probes_count INT,
    __build_ids_coverage_source VARCHAR ARRAY,
    __merged_probes BIT,
    __covered_probes INT,
    __probes_coverage_ratio FLOAT,
    __associated_test_definition_ids VARCHAR ARRAY
) AS $$
BEGIN
    RETURN QUERY
    WITH
	Risks AS (
		SELECT * FROM raw_data.get_risks(input_build_id, input_baseline_build_id, methods_class_name_pattern, methods_method_name_pattern)
	),
    MatchingMethods AS (
        WITH
        SameGroupAndAppMethods AS (
            SELECT * FROM raw_data.get_same_group_and_app_methods(input_build_id)
        )
        SELECT
			Risks.__classname AS classname,
			Risks.__signature AS signature,
			Risks.__body_checksum AS body_checksum,
			Risks.__risk_type AS risk_type,
			methods.build_id,
			methods.probe_start_pos,
			methods.probes_count
        FROM SameGroupAndAppMethods methods
        JOIN Risks ON
            Risks.__body_checksum = methods.body_checksum
            AND Risks.__signature = methods.signature
		ORDER BY Risks.__body_checksum
	),
    MatchingInstances AS (
        SELECT
            MatchingMethods.*,
            instances.id as instance_id
        FROM raw_data.instances instances
        JOIN MatchingMethods ON
            MatchingMethods.build_id = instances.build_id
    ),
	MatchingCoverageByTest AS (
        SELECT
            MatchingInstances.signature,
            MatchingInstances.body_checksum,
            MatchingInstances.build_id as build_id_coverage_source,
            BIT_OR(SUBSTRING(coverage.probes FROM MatchingInstances.probe_start_pos + 1 FOR MatchingInstances.probes_count)) as merged_probes,
            coverage.test_id as test_id
        FROM raw_data.coverage coverage
        JOIN MatchingInstances ON MatchingInstances.instance_id = coverage.instance_id AND MatchingInstances.classname = coverage.classname
        GROUP BY
            MatchingInstances.signature,
            MatchingInstances.body_checksum,
            MatchingInstances.build_id,
            coverage.test_id
    ),
    MatchingCoverage AS (
        SELECT
            MatchingCoverageByTest.signature,
            MatchingCoverageByTest.body_checksum,
            ARRAY_AGG(DISTINCT(MatchingCoverageByTest.build_id_coverage_source)) as build_ids_coverage_source,
            ARRAY_AGG(DISTINCT(MatchingCoverageByTest.test_id)) as associated_test_definition_ids,
            BIT_OR(MatchingCoverageByTest.merged_probes) as merged_probes
        FROM
            MatchingCoverageByTest
        WHERE BIT_COUNT(MatchingCoverageByTest.merged_probes) > 0
        GROUP BY
            MatchingCoverageByTest.signature,
            MatchingCoverageByTest.body_checksum,
            BIT_LENGTH(MatchingCoverageByTest.merged_probes)
    )
    SELECT
        Risks.__risk_type,
        Risks.__build_id,
        Risks.__name,
        Risks.__params,
        Risks.__return_type,
        Risks.__classname,
        Risks.__body_checksum,
        Risks.__signature,
        Risks.__probes_count,
        MatchingCoverage.build_ids_coverage_source,
        MatchingCoverage.merged_probes,
		COALESCE(CAST(BIT_COUNT(MatchingCoverage.merged_probes) AS INT), 0),
		COALESCE(CAST(BIT_COUNT(MatchingCoverage.merged_probes) AS FLOAT) / Risks.__probes_count, 0.0) AS probes_coverage_ratio,
        MatchingCoverage.associated_test_definition_ids
    FROM Risks
    LEFT JOIN MatchingCoverage ON Risks.__body_checksum = MatchingCoverage.body_checksum
        AND Risks.__signature = MatchingCoverage.signature
    ;
END;
$$ LANGUAGE plpgsql;



-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_risks(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR,

	methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

	test_definition_ids VARCHAR[] DEFAULT NULL,
    test_names VARCHAR[] DEFAULT NULL,
    test_results VARCHAR[] DEFAULT NULL,
    test_runners VARCHAR[] DEFAULT NULL,

	coverage_created_at_start TIMESTAMP DEFAULT NULL,
    coverage_created_at_end TIMESTAMP DEFAULT NULL
) RETURNS TABLE (
    __risk_type TEXT,
    __build_id VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
    __classname VARCHAR,
    __body_checksum VARCHAR,
    __signature VARCHAR,
    __probes_count INT,
    __merged_probes BIT,
    __covered_probes INT,
    __probes_coverage_ratio FLOAT,
    __associated_test_definition_ids VARCHAR ARRAY
) AS $$
BEGIN
    RETURN QUERY
	WITH
	Risks AS (
		SELECT * FROM raw_data.get_risks(input_build_id, input_baseline_build_id, methods_class_name_pattern, methods_method_name_pattern)
	),
	Instances AS (
		SELECT *
		FROM
			raw_data.get_instance_ids(input_build_id)
	),
	ClassesCoverage AS (
        WITH TestLaunchIds AS (
            SELECT *
            FROM raw_data.get_test_launch_ids(
                split_part(input_build_id, ':', 1),
                test_definition_ids,
                test_names,
                test_results,
                test_runners
            )
        )
		SELECT coverage.*
		FROM raw_data.coverage coverage
		JOIN Instances ON coverage.instance_id = Instances.__id
        LEFT JOIN TestLaunchIds ON coverage.test_id = TestLaunchIds.__id
        WHERE (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
            AND (coverage_created_at_end IS NULL OR coverage.created_at <= coverage_created_at_end)
	),
	RisksCoverage AS (
		WITH
		MethodsCoverage AS (
			SELECT
				Risks.__risk_type AS risk_type,
				Risks.__build_id AS build_id,
				Risks.__name AS name,
				Risks.__params AS params,
				Risks.__return_type AS return_type,
				Risks.__classname AS classname,
				Risks.__body_checksum AS body_checksum,
				Risks.__signature AS signature,
				Risks.__probes_count AS probes_count,
				SUBSTRING(ClassesCoverage.probes FROM Risks.__probe_start_pos + 1 FOR Risks.__probes_count) as probes,
				ClassesCoverage.test_id
			FROM Risks
			LEFT JOIN ClassesCoverage ON Risks.__classname = ClassesCoverage.classname
		)
		SELECT *
		FROM
			MethodsCoverage
	)
	SELECT
		RisksCoverage.risk_type,
		RisksCoverage.build_id,
		RisksCoverage.name,
		RisksCoverage.params,
		RisksCoverage.return_type,
		RisksCoverage.classname,
		RisksCoverage.body_checksum,
		RisksCoverage.signature,
		RisksCoverage.probes_count,
		BIT_OR(RisksCoverage.probes) AS merged_probes,
		COALESCE(CAST(BIT_COUNT(BIT_OR(RisksCoverage.probes)) AS INT), 0) AS covered_probes,
		COALESCE(CAST(BIT_COUNT(BIT_OR(RisksCoverage.probes)) AS FLOAT) / RisksCoverage.probes_count, 0.0) AS probes_coverage_ratio,
		ARRAY_AGG(DISTINCT(RisksCoverage.test_id)) AS associated_test_id
	FROM
		RisksCoverage
	GROUP BY
		RisksCoverage.risk_type,
		RisksCoverage.build_id,
		RisksCoverage.name,
		RisksCoverage.params,
		RisksCoverage.return_type,
		RisksCoverage.classname,
		RisksCoverage.body_checksum,
		RisksCoverage.signature,
		RisksCoverage.probes_count,
		BIT_LENGTH(RisksCoverage.probes);
END;
$$ LANGUAGE plpgsql;


-----------------------------------------------------------------

-----------------------------------------------------------------
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

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_aggregate_coverage_total_percent(
	_input_build_id VARCHAR
)
RETURNS FLOAT AS $$
BEGIN
RETURN (
WITH
	Probes AS (
		SELECT
			SUM(__probes_count) AS probes_count,
			SUM(__covered_probes) AS covered_probes
		FROM
			raw_data.get_aggregate_coverage_by_methods_list(_input_build_id)
	)
	SELECT
		COALESCE(CAST(Probes.covered_probes AS FLOAT) / CAST(Probes.probes_count AS FLOAT), 0) AS coverage_ratio
	FROM
		Probes
);
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------

-----------------------------------------------------------------
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
-- NOTE: this function is guaranteed to return all methods for input_build_id
-- TODO add test
-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_same_group_and_app_methods(
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
    class_annotations VARCHAR
)
AS $$
BEGIN
    RETURN QUERY
    SELECT methods.*
    FROM raw_data.builds original_build
    JOIN raw_data.builds related_build ON related_build.group_id = original_build.group_id AND related_build.app_id = original_build.app_id
    JOIN raw_data.methods methods ON methods.build_id = related_build.id
    WHERE original_build.id = input_build_id
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
        )
    ;
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_test_launches(input_build_id VARCHAR)
RETURNS TABLE (
    __id VARCHAR,
    __group_id VARCHAR,
    __test_definition_id VARCHAR,
    __name VARCHAR,
    __runner VARCHAR,
    __path VARCHAR,
    __test_task_id VARCHAR,
    __result VARCHAR,
    __created_at TIMESTAMP
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    WITH
    Build AS (
        SELECT *
        FROM raw_data.builds
        WHERE id = input_build_id
    ),
    Instances AS (
        SELECT *
        FROM raw_data.get_instance_ids(input_build_id)
    ),
    Coverage AS (
        SELECT DISTINCT test_id
        FROM raw_data.coverage coverage
        JOIN Instances ON Instances.__id = coverage.instance_id
    ),
    TestLaunches AS (
        SELECT
	        launch.id,
	        launch.group_id,
	        launch.test_definition_id,
			definitions.name,
			definitions.runner,
			definitions.path,
	        sessions.test_task_id,
	        launch.result,
	        launch.created_at
        FROM raw_data.test_launches launch
        JOIN Coverage ON Coverage.test_id = launch.id
		JOIN raw_data.test_definitions definitions on definitions.id = launch.test_definition_id
        JOIN raw_data.test_sessions sessions ON launch.test_session_id = sessions.id
    )
    SELECT *
    FROM TestLaunches;
END;
$$;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_test_tasks(
	input_build_id VARCHAR,

    methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

    test_definition_ids VARCHAR[] DEFAULT NULL,
    test_names VARCHAR[] DEFAULT NULL,
    test_results VARCHAR[] DEFAULT NULL,
    test_runners VARCHAR[] DEFAULT NULL,

    coverage_created_at_start TIMESTAMP DEFAULT NULL,
    coverage_created_at_end TIMESTAMP DEFAULT NULL
) RETURNS TABLE (
    __test_task_id VARCHAR,
    __covered_probes BIGINT,
    __total_probes BIGINT,
    __coverage_ratio FLOAT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    WITH
    Methods AS (
        SELECT *
        FROM raw_data.get_methods(
            input_build_id,
            methods_class_name_pattern,
            methods_method_name_pattern
        )
    ),
    Coverage AS (
        WITH
        InstanceIds AS (
            SELECT * FROM raw_data.get_instance_ids(input_build_id)
        ),
        TestLaunchIds AS (
            SELECT *
            FROM raw_data.get_test_launch_ids(
                split_part(input_build_id, ':', 1),
                test_definition_ids,
                test_names,
                test_results,
                test_runners
            )
        )
        SELECT
            Coverage.classname,
            Coverage.test_id,
            Coverage.probes
        FROM raw_data.coverage coverage
        WHERE coverage.instance_id IN (SELECT __id FROM InstanceIds)
            AND coverage.test_id IN (SELECT __id FROM TestLaunchIds)
            AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
            AND (coverage_created_at_end IS NULL OR coverage.created_at <= coverage_created_at_end)
    ),
    CoverageByTestTaskId AS (
        WITH
            ProbesByTestTaskPerMethod AS (
                SELECT
                    COALESCE(sessions.test_task_id, 'UNGROUPED') AS test_task_id,
                    Methods.signature,
                    BIT_OR(SUBSTRING(Coverage.probes FROM Methods.probe_start_pos + 1 FOR Methods.probes_count)) AS probes
                FROM Coverage
                JOIN Methods ON Methods.classname = Coverage.classname
                LEFT JOIN raw_data.test_launches launches ON launches.id = Coverage.test_id
                LEFT JOIN raw_data.test_sessions sessions ON launches.test_session_id = sessions.id
                GROUP BY
                    sessions.test_task_id,
                    Methods.signature
            ),
            CoveredProbesCount AS (
                SELECT
                    coverage.test_task_id,
                    coverage.signature,
                    BIT_COUNT(coverage.probes) as covered_probes
                FROM ProbesByTestTaskPerMethod coverage
                WHERE BIT_COUNT(probes) > 0
            )
            SELECT
                test_task_id,
                SUM(covered_probes) as covered_probes
            FROM CoveredProbesCount counts
            GROUP BY test_task_id
    ),
    TotalCoverage AS (
        WITH
        Classes AS (
            SELECT
                classname,
                SUM(Methods.probes_count) AS probes_count
            FROM Methods
            GROUP BY classname
        ),
        ClassesCoverage AS (
            WITH A AS (
                SELECT
                    classname,
                    BIT_COUNT(BIT_OR(probes)) AS covered_probes_count
                FROM Coverage
                GROUP BY coverage.classname, BIT_LENGTH(probes)
            )
            SELECT
                classname,
                -- pick highest number of covered probes. Variability happens in cases when the same signature & body_checksum yield different number of probes
                MAX(covered_probes_count) AS covered_probes_count
            FROM A
            GROUP BY classname
        ),
        Sums AS (
            SELECT
                SUM(ClassesCoverage.covered_probes_count) AS covered_probes,
                SUM(Classes.probes_count) AS total_probes
            FROM Classes
            LEFT JOIN ClassesCoverage ON Classes.classname = ClassesCoverage.classname
        )
        SELECT
            Sums.covered_probes::BIGINT,
            Sums.total_probes::BIGINT,
			COALESCE(CAST(Sums.covered_probes AS FLOAT) / NULLIF(CAST(Sums.total_probes AS FLOAT), 0), 0) AS coverage_ratio
        FROM Sums
    )
    SELECT
        coverage.test_task_id,
        coverage.covered_probes::BIGINT,
        TotalCoverage.total_probes::BIGINT,
	    COALESCE(CAST(coverage.covered_probes AS FLOAT) / NULLIF(CAST(TotalCoverage.total_probes AS FLOAT), 0), 0) AS coverage_ratio
    FROM CoverageByTestTaskId coverage
    CROSS JOIN TotalCoverage

    UNION ALL

    SELECT
        'TOTAL' as test_task_id,
        *
    FROM TotalCoverage
    ;
END;
$$;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_aggregate_coverage_by_test_tasks(
	input_build_id VARCHAR,

    methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

    test_definition_ids VARCHAR[] DEFAULT NULL,
    test_names VARCHAR[] DEFAULT NULL,
    test_results VARCHAR[] DEFAULT NULL,
    test_runners VARCHAR[] DEFAULT NULL,

    coverage_created_at_start TIMESTAMP DEFAULT NULL,
    coverage_created_at_end TIMESTAMP DEFAULT NULL
) RETURNS TABLE (
    __test_task_id VARCHAR,
    __covered_probes BIGINT,
    __total_probes BIGINT,
    __coverage_ratio FLOAT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    WITH
    Methods AS (
        SELECT *
        FROM raw_data.get_methods(input_build_id, methods_class_name_pattern, methods_method_name_pattern)
    ),
    MatchingMethods AS (
        WITH
        SameGroupAndAppMethods AS (
            SELECT *
            FROM raw_data.get_same_group_and_app_methods(input_build_id, methods_class_name_pattern, methods_method_name_pattern)
        )
        SELECT
            Methods.signature,
            Methods.body_checksum,
            Methods.classname,
            same_methods.build_id,
            same_methods.probe_start_pos,
            same_methods.probes_count
        FROM SameGroupAndAppMethods same_methods
        JOIN Methods ON
            Methods.body_checksum = same_methods.body_checksum
            AND Methods.signature = same_methods.signature
        ORDER BY Methods.body_checksum
    ),
    MatchingInstances AS (
        SELECT
            MatchingMethods.*,
            instances.id as instance_id
        FROM raw_data.instances instances
        JOIN MatchingMethods ON
            MatchingMethods.build_id = instances.build_id
    ),
    MatchingCoverageByTest AS (
        WITH TestLaunchIds AS (
            SELECT *
            FROM raw_data.get_test_launch_ids(
                split_part(input_build_id, ':', 1),
                test_definition_ids,
                test_names,
                test_results,
                test_runners
            )
        ),
        MergedCoverageByTestPerMethod AS (
            SELECT
                MatchingInstances.signature,
                MatchingInstances.body_checksum,
                MatchingInstances.build_id as build_id_coverage_source,
                BIT_OR(SUBSTRING(coverage.probes FROM MatchingInstances.probe_start_pos + 1 FOR MatchingInstances.probes_count)) as probes,
                coverage.test_id as test_id
            FROM raw_data.coverage coverage
            JOIN MatchingInstances ON MatchingInstances.instance_id = coverage.instance_id AND MatchingInstances.classname = coverage.classname
            WHERE coverage.test_id IN (SELECT __id FROM TestLaunchIds)
                AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
                AND (coverage_created_at_end IS NULL OR coverage.created_at <= coverage_created_at_end)
            GROUP BY
                MatchingInstances.signature,
                MatchingInstances.body_checksum,
                MatchingInstances.build_id,
                coverage.test_id
        )
        SELECT *
        FROM MergedCoverageByTestPerMethod
        WHERE BIT_COUNT(MergedCoverageByTestPerMethod.probes) > 0
    ),
    CoverageByTestTaskId AS (
        WITH
        ProbesByTestTaskPerMethod AS (
            SELECT
                COALESCE(sessions.test_task_id, 'UNGROUPED') AS test_task_id,
                Methods.signature,
                BIT_OR(coverage.probes) AS probes
            FROM MatchingCoverageByTest coverage
            JOIN Methods ON Methods.signature = coverage.signature
            LEFT JOIN raw_data.test_launches launches ON launches.id = coverage.test_id
            LEFT JOIN raw_data.test_sessions sessions ON launches.test_session_id = sessions.id
            GROUP BY
                sessions.test_task_id,
                Methods.signature
        ),
        CoveredProbesCount AS (
            SELECT
                coverage.test_task_id,
                coverage.signature,
                BIT_COUNT(coverage.probes) as covered_probes
            FROM ProbesByTestTaskPerMethod coverage
            -- WHERE BIT_COUNT(probes) > 0 -- already filtered above at MatchingCoverageByTest CTE
        )
        SELECT
            test_task_id,
            SUM(covered_probes) as covered_probes
        FROM CoveredProbesCount counts
        GROUP BY test_task_id
    ),
    TotalCoverage AS (
        WITH
        MatchingCoverage AS (
            SELECT
                MatchingCoverageByTest.signature,
                MatchingCoverageByTest.body_checksum,
                BIT_OR(MatchingCoverageByTest.probes) as probes
            FROM
                MatchingCoverageByTest
            WHERE BIT_COUNT(MatchingCoverageByTest.probes) > 0
            GROUP BY
                MatchingCoverageByTest.signature,
                MatchingCoverageByTest.body_checksum
        ),
        Sums AS (
            SELECT
                SUM(Methods.probes_count) AS total_probes,
                SUM(BIT_COUNT(MatchingCoverage.probes)) AS covered_probes
            FROM Methods
            LEFT JOIN MatchingCoverage ON Methods.body_checksum = MatchingCoverage.body_checksum
                AND Methods.signature = MatchingCoverage.signature
        )
        SELECT
            Sums.covered_probes::BIGINT,
            Sums.total_probes::BIGINT,
			COALESCE(CAST(Sums.covered_probes AS FLOAT) / NULLIF(CAST(Sums.total_probes AS FLOAT), 0), 0) AS coverage_ratio
        FROM Sums
    )
    SELECT
        coverage.test_task_id,
        coverage.covered_probes::BIGINT,
        TotalCoverage.total_probes::BIGINT,
	    COALESCE(CAST(coverage.covered_probes AS FLOAT) / NULLIF(CAST(TotalCoverage.total_probes AS FLOAT), 0), 0) AS coverage_ratio
    FROM CoverageByTestTaskId coverage
    CROSS JOIN TotalCoverage

    UNION ALL

    SELECT
        'TOTAL' as test_task_id,
        *
    FROM TotalCoverage
    ;
END;
$$;

-----------------------------------------------------------------

-----------------------------------------------------------------

CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_methods_list_v2(
    input_build_id VARCHAR,

	methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

	coverage_created_at_start TIMESTAMP DEFAULT NULL,

    test_task_ids VARCHAR[] DEFAULT NULL,
    test_results VARCHAR[] DEFAULT NULL,
    coverage_env_ids VARCHAR[] DEFAULT NULL,
    test_tags VARCHAR DEFAULT NULL
)
RETURNS TABLE (
	__signature VARCHAR,
    __classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
    __probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
    WITH
    TargetMethods AS (
        SELECT
            methods.signature,
            methods.name,
            methods.classname,
            methods.params,
            methods.return_type,
            methods.body_checksum,
            methods.probes_count
        FROM raw_data.view_methods_with_rules methods
        WHERE methods.build_id = input_build_id
            AND (methods_class_name_pattern IS NULL OR methods.classname LIKE methods_class_name_pattern)
            AND (methods_method_name_pattern IS NULL OR methods.name LIKE methods_method_name_pattern)
    ),
    TargetMethodCoverage AS (
        SELECT
            target.signature,
            MIN(target.classname) AS classname,
            MIN(target.name) AS name,
            MIN(target.params) AS params,
            MIN(target.return_type) AS return_type,
            MAX(target.probes_count) AS probes_count,
            BIT_COUNT(BIT_OR(coverage.probes)) AS covered_probes
        FROM TargetMethods target
        LEFT JOIN raw_data.view_methods_coverage coverage ON target.signature = coverage.signature
            AND target.body_checksum = coverage.body_checksum
            AND target.probes_count = BIT_LENGTH(coverage.probes)
            AND coverage.build_id = input_build_id
            --filter by test tasks and test results
            AND (test_task_ids IS NULL OR coverage.test_task_id = ANY (test_task_ids))
            AND (test_results IS NULL OR coverage.test_result = ANY(test_results))
            --filter by coverage created_at
            AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
            --filter by envs
            AND (coverage_env_ids IS NULL OR coverage.env_id = ANY(coverage_env_ids))
            --filter by test tags
            AND (test_tags IS NULL OR coverage.test_definition_id IN (
                SELECT id
                FROM raw_data.test_definitions def
                WHERE (test_tags = ANY(def.tags))
            ))
        GROUP BY target.signature
    )
    SELECT
        coverage.signature,
        coverage.classname::VARCHAR,
        coverage.name::VARCHAR,
        coverage.params::VARCHAR,
        coverage.return_type::VARCHAR,
        coverage.probes_count::INT as __probes_count,
        COALESCE(coverage.covered_probes, 0)::INT as __covered_probes,
        (coverage.probes_count - COALESCE(coverage.covered_probes, 0))::INT AS missed_probes,
        COALESCE(CAST(coverage.covered_probes AS FLOAT) / coverage.probes_count, 0.0) AS probes_coverage_ratio
    FROM TargetMethodCoverage coverage
    ORDER BY missed_probes DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 5000;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_aggregate_coverage_by_methods_list_v2(
	input_build_id VARCHAR,
	methods_class_name_pattern VARCHAR DEFAULT NULL,
	methods_method_name_pattern VARCHAR DEFAULT NULL,
	coverage_created_at_start TIMESTAMP DEFAULT NULL,
	test_task_ids VARCHAR[] DEFAULT NULL,
	test_results VARCHAR[] DEFAULT NULL,
	coverage_branches VARCHAR[] DEFAULT NULL,
	coverage_env_ids VARCHAR[] DEFAULT NULL,
    test_tags VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    __signature VARCHAR,
    __classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
    __probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
	WITH
    TargetMethods AS (
    	SELECT
	    	methods.signature,
			methods.name,
			methods.classname,
			methods.params,
			methods.return_type,
			methods.body_checksum,
			methods.probes_count
	 	FROM raw_data.view_methods_with_rules methods
	 	WHERE methods.build_id = input_build_id
		    AND (methods_class_name_pattern IS NULL OR methods.classname LIKE methods_class_name_pattern)
			AND (methods_method_name_pattern IS NULL OR methods.name LIKE methods_method_name_pattern)
  	),
	TargetMethodCoverage AS (
		SELECT
		    target.signature,
			MIN(target.classname) AS classname,
			MIN(target.name) AS name,
			MIN(target.params) AS params,
			MIN(target.return_type) AS return_type,
			MAX(target.probes_count) AS probes_count,
			BIT_COUNT(BIT_OR(coverage.probes)) AS covered_probes
		FROM TargetMethods target
		LEFT JOIN raw_data.view_methods_coverage coverage ON target.signature = coverage.signature
			AND target.probes_count = BIT_LENGTH(coverage.probes)
			AND target.body_checksum = coverage.body_checksum
			AND coverage.group_id = split_part(input_build_id, ':', 1)
		 	AND coverage.app_id = split_part(input_build_id, ':', 2)
			--filter by coverage created_at
			AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
			--filter by test tasks and test results
			AND (test_task_ids IS NULL OR coverage.test_task_id = ANY (test_task_ids))
            AND (test_results IS NULL OR coverage.test_result = ANY(test_results))
			--filter by branches
			AND (coverage_branches IS NULL OR coverage.branch = ANY(coverage_branches))
			--filter by envs
			AND (coverage_env_ids IS NULL OR coverage.env_id = ANY(coverage_env_ids))
			--filter by test tags
            AND (test_tags IS NULL OR coverage.test_definition_id IN (
                SELECT id
                FROM raw_data.test_definitions def
                WHERE (test_tags = ANY(def.tags))
            ))
		GROUP BY target.signature
  	)
	SELECT
		coverage.signature,
		coverage.classname::VARCHAR,
		coverage.name::VARCHAR,
		coverage.params::VARCHAR,
		coverage.return_type::VARCHAR,
		coverage.probes_count::INT,
		COALESCE(coverage.covered_probes, 0)::INT,
		(coverage.probes_count - COALESCE(coverage.covered_probes, 0))::INT AS missed_probes,
		COALESCE(CAST(coverage.covered_probes AS FLOAT) / coverage.probes_count, 0.0) AS probes_coverage_ratio
	FROM TargetMethodCoverage coverage
	ORDER BY missed_probes DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 7000;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_aggregate_coverage_by_risks_v2(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR,

    methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

	coverage_created_at_start TIMESTAMP DEFAULT NULL,
	test_task_ids VARCHAR[] DEFAULT NULL,
	test_results VARCHAR[] DEFAULT NULL,
	coverage_branches VARCHAR[] DEFAULT NULL,
	coverage_env_ids VARCHAR[] DEFAULT NULL,
    test_tags VARCHAR DEFAULT NULL
)
RETURNS TABLE (
	__risk_type VARCHAR,
	__signature VARCHAR,
	__classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
	__probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
	WITH
	Risks AS (
        SELECT
			risks.__signature AS signature,
			risks.__classname AS classname,
			risks.__name AS name,
			risks.__params AS params,
			risks.__return_type AS return_type,
		    risks.__risk_type AS risk_type,
            risks.__name AS risk_name,
            risks.__body_checksum AS body_checksum,
			risks.__probes_count AS probes_count
        FROM raw_data.get_risks(input_build_id, input_baseline_build_id, methods_class_name_pattern, methods_method_name_pattern) risks
    ),
    RisksCoverage AS (
		SELECT
		    risks.signature,
			MIN(risks.risk_type) AS risk_type,
			MIN(risks.classname) AS classname,
			MIN(risks.name) AS name,
			MIN(risks.params) AS params,
			MIN(risks.return_type) AS return_type,
			MAX(risks.probes_count) AS probes_count,
			BIT_COUNT(BIT_OR(coverage.probes)) AS covered_probes
		FROM Risks risks
		LEFT JOIN raw_data.view_methods_coverage coverage ON risks.signature = coverage.signature
			AND risks.probes_count = BIT_LENGTH(coverage.probes)
			AND risks.body_checksum = coverage.body_checksum
			AND coverage.group_id = split_part(input_build_id, ':', 1)
		 	AND coverage.app_id = split_part(input_build_id, ':', 2)
			--filter by coverage created_at
			AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
			--filter by test tasks and test results
			AND (test_task_ids IS NULL OR coverage.test_task_id = ANY (test_task_ids))
            AND (test_results IS NULL OR coverage.test_result = ANY(test_results))
			--filter by branches
			AND (coverage_branches IS NULL OR coverage.branch = ANY(coverage_branches))
			--filter by envs
			AND (coverage_env_ids IS NULL OR coverage.env_id = ANY(coverage_env_ids))
            --filter by test tags
            AND (test_tags IS NULL OR coverage.test_definition_id IN (
                SELECT id
                FROM raw_data.test_definitions def
                WHERE (test_tags = ANY(def.tags))
            ))
		GROUP BY risks.signature
  	)
    SELECT
        coverage.risk_type::VARCHAR,
		coverage.signature,
		coverage.classname::VARCHAR,
		coverage.name::VARCHAR,
		coverage.params::VARCHAR,
		coverage.return_type::VARCHAR,
		coverage.probes_count::INT,
		COALESCE(coverage.covered_probes, 0)::INT AS covered_probes,
		(coverage.probes_count - COALESCE(coverage.covered_probes, 0))::INT AS missed_probes,
		COALESCE(CAST(coverage.covered_probes AS FLOAT) / coverage.probes_count, 0.0) AS probes_coverage_ratio
    FROM RisksCoverage coverage
	ORDER BY missed_probes DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 7000;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_risks_v2(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR,

    methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

	coverage_created_at_start TIMESTAMP DEFAULT NULL,
	test_task_ids VARCHAR[] DEFAULT NULL,
	test_results VARCHAR[] DEFAULT NULL,
	coverage_env_ids VARCHAR[] DEFAULT NULL,
	test_tags VARCHAR DEFAULT NULL
)
RETURNS TABLE (
	__risk_type VARCHAR,
	__signature VARCHAR,
	__classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
	__probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
	WITH
	Risks AS (
        SELECT
			risks.__signature AS signature,
			risks.__classname AS classname,
			risks.__name AS name,
			risks.__params AS params,
			risks.__return_type AS return_type,
		    risks.__risk_type AS risk_type,
            risks.__name AS risk_name,
            risks.__body_checksum AS body_checksum,
			risks.__probes_count AS probes_count
        FROM raw_data.get_risks(input_build_id, input_baseline_build_id, methods_class_name_pattern, methods_method_name_pattern) risks
    ),
    RisksCoverage AS (
		SELECT
		    risks.signature,
			MIN(risks.risk_type) AS risk_type,
			MIN(risks.classname) AS classname,
			MIN(risks.name) AS name,
			MIN(risks.params) AS params,
			MIN(risks.return_type) AS return_type,
			MAX(risks.probes_count) AS probes_count,
			BIT_COUNT(BIT_OR(coverage.probes)) AS covered_probes
		FROM Risks risks
		LEFT JOIN raw_data.view_methods_coverage coverage ON risks.signature = coverage.signature
			AND risks.probes_count = BIT_LENGTH(coverage.probes)
			AND risks.body_checksum = coverage.body_checksum
			AND coverage.build_id = input_build_id
			--filter by coverage created_at
			AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
			--filter by test tasks and test results
			AND (test_task_ids IS NULL OR coverage.test_task_id = ANY (test_task_ids))
            AND (test_results IS NULL OR coverage.test_result = ANY(test_results))
			--filter by envs
			AND (coverage_env_ids IS NULL OR coverage.env_id = ANY(coverage_env_ids))
			--filter by test tags
            AND (test_tags IS NULL OR coverage.test_definition_id IN (
                SELECT id
                FROM raw_data.test_definitions def
                WHERE (test_tags = ANY(def.tags))
            ))
		GROUP BY risks.signature
  	)
    SELECT
        coverage.risk_type::VARCHAR,
		coverage.signature,
		coverage.classname::VARCHAR,
		coverage.name::VARCHAR,
		coverage.params::VARCHAR,
		coverage.return_type::VARCHAR,
		coverage.probes_count::INT,
		COALESCE(coverage.covered_probes, 0)::INT AS covered_probes,
		(coverage.probes_count - COALESCE(coverage.covered_probes, 0))::INT AS missed_probes,
		COALESCE(CAST(coverage.covered_probes AS FLOAT) / coverage.probes_count, 0.0) AS probes_coverage_ratio
    FROM RisksCoverage coverage
	ORDER BY missed_probes DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 5000;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_materialized_coverage_by_methods_list_v2(
    input_build_id VARCHAR,

	methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

	coverage_created_at_start TIMESTAMP DEFAULT NULL,

    test_task_ids VARCHAR[] DEFAULT NULL,
    test_results VARCHAR[] DEFAULT NULL,
    coverage_env_ids VARCHAR[] DEFAULT NULL,
    test_tags VARCHAR DEFAULT NULL
)
RETURNS TABLE (
	__signature VARCHAR,
    __classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
    __probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
    WITH
    TargetMethods AS (
        SELECT
            methods.signature,
            methods.name,
            methods.classname,
            methods.params,
            methods.return_type,
            methods.body_checksum,
            methods.probes_count
        FROM raw_data.view_methods_with_rules methods
        WHERE methods.build_id = input_build_id
            AND (methods_class_name_pattern IS NULL OR methods.classname LIKE methods_class_name_pattern)
            AND (methods_method_name_pattern IS NULL OR methods.name LIKE methods_method_name_pattern)
    ),
    TargetMethodCoverage AS (
        SELECT
            target.signature,
            MIN(target.classname) AS classname,
            MIN(target.name) AS name,
            MIN(target.params) AS params,
            MIN(target.return_type) AS return_type,
            MAX(target.probes_count) AS probes_count,
            BIT_COUNT(BIT_OR(coverage.probes)) AS covered_probes
        FROM TargetMethods target
        LEFT JOIN raw_data.matview_methods_coverage coverage ON target.signature = coverage.signature
            AND target.body_checksum = coverage.body_checksum
            AND target.probes_count = BIT_LENGTH(coverage.probes)
            AND coverage.build_id = input_build_id
            --filter by test tasks and test results
            AND (test_task_ids IS NULL OR coverage.test_task_id = ANY (test_task_ids))
            AND (test_results IS NULL OR coverage.test_result = ANY(test_results))
            --filter by coverage created_at
            AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
            --filter by envs
            AND (coverage_env_ids IS NULL OR coverage.env_id = ANY(coverage_env_ids))
            --filter by test tags
            AND (test_tags IS NULL OR coverage.test_definition_id IN (
                SELECT id
                FROM raw_data.test_definitions def
                WHERE (test_tags = ANY(def.tags))
            ))
        GROUP BY target.signature
    )
    SELECT
        coverage.signature,
        coverage.classname::VARCHAR,
        coverage.name::VARCHAR,
        coverage.params::VARCHAR,
        coverage.return_type::VARCHAR,
        coverage.probes_count::INT as __probes_count,
        COALESCE(coverage.covered_probes, 0)::INT as __covered_probes,
        (coverage.probes_count - COALESCE(coverage.covered_probes, 0))::INT AS missed_probes,
        COALESCE(CAST(coverage.covered_probes AS FLOAT) / coverage.probes_count, 0.0) AS probes_coverage_ratio
    FROM TargetMethodCoverage coverage
    ORDER BY missed_probes DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 5000;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_materialized_aggregate_coverage_by_methods_list_v2(
	input_build_id VARCHAR,
	methods_class_name_pattern VARCHAR DEFAULT NULL,
	methods_method_name_pattern VARCHAR DEFAULT NULL,
	coverage_created_at_start TIMESTAMP DEFAULT NULL,
	test_task_ids VARCHAR[] DEFAULT NULL,
	test_results VARCHAR[] DEFAULT NULL,
	coverage_branches VARCHAR[] DEFAULT NULL,
	coverage_env_ids VARCHAR[] DEFAULT NULL,
    test_tags VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    __signature VARCHAR,
    __classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
    __probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
	WITH
    TargetMethods AS (
    	SELECT
	    	methods.signature,
			methods.name,
			methods.classname,
			methods.params,
			methods.return_type,
			methods.body_checksum,
			methods.probes_count
	 	FROM raw_data.view_methods_with_rules methods
	 	WHERE methods.build_id = input_build_id
			AND (methods_class_name_pattern IS NULL OR methods.classname LIKE methods_class_name_pattern)
			AND (methods_method_name_pattern IS NULL OR methods.name LIKE methods_method_name_pattern)
  	),
	TargetMethodCoverage AS (
		SELECT
		    target.signature,
			MIN(target.classname) AS classname,
			MIN(target.name) AS name,
			MIN(target.params) AS params,
			MIN(target.return_type) AS return_type,
			MAX(target.probes_count) AS probes_count,
			BIT_COUNT(BIT_OR(coverage.probes)) AS covered_probes
		FROM TargetMethods target
		LEFT JOIN raw_data.matview_methods_coverage coverage ON target.signature = coverage.signature
			AND target.probes_count = BIT_LENGTH(coverage.probes)
			AND target.body_checksum = coverage.body_checksum
			AND coverage.group_id = split_part(input_build_id, ':', 1)
		 	AND coverage.app_id = split_part(input_build_id, ':', 2)
			--filter by coverage created_at
			AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
			--filter by test tasks and test results
			AND (test_task_ids IS NULL OR coverage.test_task_id = ANY (test_task_ids))
            AND (test_results IS NULL OR coverage.test_result = ANY(test_results))
			--filter by branches
			AND (coverage_branches IS NULL OR coverage.branch = ANY(coverage_branches))
			--filter by envs
			AND (coverage_env_ids IS NULL OR coverage.env_id = ANY(coverage_env_ids))
            --filter by test tags
            AND (test_tags IS NULL OR coverage.test_definition_id IN (
                SELECT id
                FROM raw_data.test_definitions def
                WHERE (test_tags = ANY(def.tags))
            ))
		GROUP BY target.signature
  	)
	SELECT
		coverage.signature,
		coverage.classname::VARCHAR,
		coverage.name::VARCHAR,
		coverage.params::VARCHAR,
		coverage.return_type::VARCHAR,
		coverage.probes_count::INT,
		COALESCE(coverage.covered_probes, 0)::INT,
		(coverage.probes_count - COALESCE(coverage.covered_probes, 0))::INT AS missed_probes,
		COALESCE(CAST(coverage.covered_probes AS FLOAT) / coverage.probes_count, 0.0) AS probes_coverage_ratio
	FROM TargetMethodCoverage coverage
	ORDER BY missed_probes DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 7000;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_materialized_aggregate_coverage_by_risks_v2(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR,

    methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

	coverage_created_at_start TIMESTAMP DEFAULT NULL,
	test_task_ids VARCHAR[] DEFAULT NULL,
	test_results VARCHAR[] DEFAULT NULL,
	coverage_branches VARCHAR[] DEFAULT NULL,
	coverage_env_ids VARCHAR[] DEFAULT NULL,
    test_tags VARCHAR DEFAULT NULL
)
RETURNS TABLE (
	__risk_type VARCHAR,
	__signature VARCHAR,
	__classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
	__probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
	WITH
	Risks AS (
        SELECT
			risks.__signature AS signature,
			risks.__classname AS classname,
			risks.__name AS name,
			risks.__params AS params,
			risks.__return_type AS return_type,
		    risks.__risk_type AS risk_type,
            risks.__name AS risk_name,
            risks.__body_checksum AS body_checksum,
			risks.__probes_count AS probes_count
        FROM raw_data.get_risks(input_build_id, input_baseline_build_id, methods_class_name_pattern, methods_method_name_pattern) risks
    ),
    RisksCoverage AS (
		SELECT
		    risks.signature,
			MIN(risks.risk_type) AS risk_type,
			MIN(risks.classname) AS classname,
			MIN(risks.name) AS name,
			MIN(risks.params) AS params,
			MIN(risks.return_type) AS return_type,
			MAX(risks.probes_count) AS probes_count,
			BIT_COUNT(BIT_OR(coverage.probes)) AS covered_probes
		FROM Risks risks
		LEFT JOIN raw_data.matview_methods_coverage coverage ON risks.signature = coverage.signature
			AND risks.probes_count = BIT_LENGTH(coverage.probes)
			AND risks.body_checksum = coverage.body_checksum
			AND coverage.group_id = split_part(input_build_id, ':', 1)
		 	AND coverage.app_id = split_part(input_build_id, ':', 2)
			--filter by coverage created_at
			AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
			--filter by test tasks and test results
			AND (test_task_ids IS NULL OR coverage.test_task_id = ANY (test_task_ids))
            AND (test_results IS NULL OR coverage.test_result = ANY(test_results))
			--filter by branches
			AND (coverage_branches IS NULL OR coverage.branch = ANY(coverage_branches))
			--filter by envs
			AND (coverage_env_ids IS NULL OR coverage.env_id = ANY(coverage_env_ids))
            --filter by test tags
            AND (test_tags IS NULL OR coverage.test_definition_id IN (
                SELECT id
                FROM raw_data.test_definitions def
                WHERE (test_tags = ANY(def.tags))
            ))
		GROUP BY risks.signature
  	)
    SELECT
        coverage.risk_type::VARCHAR,
		coverage.signature,
		coverage.classname::VARCHAR,
		coverage.name::VARCHAR,
		coverage.params::VARCHAR,
		coverage.return_type::VARCHAR,
		coverage.probes_count::INT,
		COALESCE(coverage.covered_probes, 0)::INT AS covered_probes,
		(coverage.probes_count - COALESCE(coverage.covered_probes, 0))::INT AS missed_probes,
		COALESCE(CAST(coverage.covered_probes AS FLOAT) / coverage.probes_count, 0.0) AS probes_coverage_ratio
    FROM RisksCoverage coverage
	ORDER BY missed_probes DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 7000;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_materialized_coverage_by_risks_v2(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR,

    methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

	coverage_created_at_start TIMESTAMP DEFAULT NULL,
	test_task_ids VARCHAR[] DEFAULT NULL,
	test_results VARCHAR[] DEFAULT NULL,
	coverage_env_ids VARCHAR[] DEFAULT NULL,
    test_tags VARCHAR DEFAULT NULL
)
RETURNS TABLE (
	__risk_type VARCHAR,
	__signature VARCHAR,
	__classname VARCHAR,
    __name VARCHAR,
    __params VARCHAR,
    __return_type VARCHAR,
	__probes_count INT,
    __covered_probes INT,
    __missed_probes INT,
    __probes_coverage_ratio FLOAT
) AS $$
BEGIN
    RETURN QUERY
	WITH
	Risks AS (
        SELECT
			risks.__signature AS signature,
			risks.__classname AS classname,
			risks.__name AS name,
			risks.__params AS params,
			risks.__return_type AS return_type,
		    risks.__risk_type AS risk_type,
            risks.__name AS risk_name,
            risks.__body_checksum AS body_checksum,
			risks.__probes_count AS probes_count
        FROM raw_data.get_risks(input_build_id, input_baseline_build_id, methods_class_name_pattern, methods_method_name_pattern) risks
    ),
    RisksCoverage AS (
		SELECT
		    risks.signature,
			MIN(risks.risk_type) AS risk_type,
			MIN(risks.classname) AS classname,
			MIN(risks.name) AS name,
			MIN(risks.params) AS params,
			MIN(risks.return_type) AS return_type,
			MAX(risks.probes_count) AS probes_count,
			BIT_COUNT(BIT_OR(coverage.probes)) AS covered_probes
		FROM Risks risks
		LEFT JOIN raw_data.matview_methods_coverage coverage ON risks.signature = coverage.signature
			AND risks.probes_count = BIT_LENGTH(coverage.probes)
			AND risks.body_checksum = coverage.body_checksum
			AND coverage.build_id = input_build_id
			--filter by coverage created_at
			AND (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
			--filter by test tasks and test results
			AND (test_task_ids IS NULL OR coverage.test_task_id = ANY (test_task_ids))
            AND (test_results IS NULL OR coverage.test_result = ANY(test_results))
			--filter by envs
			AND (coverage_env_ids IS NULL OR coverage.env_id = ANY(coverage_env_ids))
            --filter by test tags
            AND (test_tags IS NULL OR coverage.test_definition_id IN (
                SELECT id
                FROM raw_data.test_definitions def
                WHERE (test_tags = ANY(def.tags))
            ))
		GROUP BY risks.signature
  	)
    SELECT
        coverage.risk_type::VARCHAR,
		coverage.signature,
		coverage.classname::VARCHAR,
		coverage.name::VARCHAR,
		coverage.params::VARCHAR,
		coverage.return_type::VARCHAR,
		coverage.probes_count::INT,
		COALESCE(coverage.covered_probes, 0)::INT AS covered_probes,
		(coverage.probes_count - COALESCE(coverage.covered_probes, 0))::INT AS missed_probes,
		COALESCE(CAST(coverage.covered_probes AS FLOAT) / coverage.probes_count, 0.0) AS probes_coverage_ratio
    FROM RisksCoverage coverage
	ORDER BY missed_probes DESC;
END;
$$ LANGUAGE plpgsql STABLE PARALLEL SAFE COST 5000;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_recommended_tests_v2(
    input_group_id VARCHAR,
	input_target_build_id VARCHAR,
	input_tests_to_skip BOOLEAN DEFAULT FALSE,
	input_test_task_id VARCHAR DEFAULT NULL,
	input_baseline_build_id VARCHAR DEFAULT NULL,
	input_coverage_period_from TIMESTAMP DEFAULT NULL
) RETURNS TABLE(
    __test_definition_id VARCHAR,
    __test_runner VARCHAR,
    __test_path VARCHAR,
    __test_name VARCHAR,
    __test_type VARCHAR,
    __group_id VARCHAR,
    __created_at TIMESTAMP,
    __tags VARCHAR[],
    __metadata JSON
) AS $$
BEGIN
    RETURN QUERY
    WITH
    Risks AS (
        SELECT
			risks.__signature AS signature,
		    risks.__risk_type AS risk_type,
            risks.__name AS risk_name,
            risks.__body_checksum AS risk_body_checksum
        FROM raw_data.get_risks(input_target_build_id, input_baseline_build_id) risks
    ),
    TargetMethods AS (
    	SELECT
	    	methods.signature,
			methods.body_checksum,
			methods.probes_count
	 	FROM raw_data.view_methods_with_rules methods
	 	WHERE methods.build_id = input_target_build_id
  	),
	Coverage AS (
		SELECT
		    coverage.test_definition_id,
			coverage.test_launch_id,
			coverage.build_id,
			(target.body_checksum != coverage.body_checksum) AS is_modified
		FROM raw_data.view_methods_coverage coverage
		JOIN TargetMethods target ON target.signature = coverage.signature
		WHERE coverage.group_id = input_group_id
		 	AND coverage.app_id = split_part(input_target_build_id, ':', 2)
		 	AND coverage.test_result = 'PASSED'
		 	AND (input_test_task_id IS NULL OR coverage.test_task_id = input_test_task_id)
		 	AND (input_coverage_period_from IS NULL OR coverage.created_at >= input_coverage_period_from)
		 	AND (input_baseline_build_id IS NULL OR target.signature IN (
				SELECT signature
				FROM Risks
		 	))
  	),
	TestsByRisks AS (
		SELECT
		    MIN(coverage.test_definition_id) AS test_definition_id,
			BOOL_OR(is_modified) AS is_modified
		FROM Coverage coverage
		GROUP BY coverage.test_launch_id
	),
	RecommendedTests AS (
		SELECT
			tests.test_definition_id AS test_definition_id
		FROM TestsByRisks tests
		GROUP BY tests.test_definition_id
		HAVING BOOL_AND(is_modified) = true
	),
	Tests AS (
		SELECT
			def.id as test_definition_id,
		   	def.name as test_name,
		   	def.runner as test_runner,
		   	def.path as test_path,
		   	def.type as test_type,
		   	def.group_id,
		   	def.created_at,
		   	def.tags,
		   	def.metadata
		FROM raw_data.test_definitions def
		WHERE def.group_id = input_group_id
			AND def.id IN (
            	SELECT launches.test_definition_id
				FROM raw_data.test_launches launches
				JOIN raw_data.test_sessions sessions ON sessions.id = launches.test_session_id
				WHERE (input_test_task_id IS NULL OR sessions.test_task_id = input_test_task_id)
			  		AND (input_coverage_period_from IS NULL OR sessions.created_at >= input_coverage_period_from)
		  		)
			-- include only recommended tests
			AND (input_tests_to_skip = TRUE OR def.id IN (
		  		SELECT DISTINCT test_definition_id
				FROM RecommendedTests
		  	))
            -- include only tests to skip
		  	AND (input_tests_to_skip IS NULL OR input_tests_to_skip = FALSE OR def.id NOT IN (
		  		SELECT DISTINCT test_definition_id
				FROM RecommendedTests
		  	))
  	)
	SELECT
		test_definition_id,
		test_runner,
		test_path,
		test_name,
        test_type,
        group_id,
        created_at,
        tags,
        metadata
	FROM Tests;
END;
$$ LANGUAGE plpgsql;
