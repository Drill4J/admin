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
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_methods(
    input_build_id VARCHAR,

	methods_class_name_pattern VARCHAR DEFAULT NULL,
    methods_method_name_pattern VARCHAR DEFAULT NULL,

	test_definition_ids VARCHAR[] DEFAULT NULL,
    test_names VARCHAR[] DEFAULT NULL,
    test_results VARCHAR[] DEFAULT NULL,
    test_runners VARCHAR[] DEFAULT NULL,

	coverage_created_at_start TIMESTAMP DEFAULT NULL,
    coverage_created_at_end TIMESTAMP DEFAULT NULL
)
RETURNS TABLE (
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
	__associated_test_definition_ids VARCHAR ARRAY,
    __associated_test_names VARCHAR ARRAY,
    __associated_test_runners VARCHAR ARRAY
--    __associated_test_results VARCHAR ARRAY
) AS $$
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
    SELECT *
    FROM raw_data.coverage coverage
    JOIN InstanceIds ON coverage.instance_id = InstanceIds.__id
    JOIN TestLaunchIds ON coverage.test_id = TestLaunchIds.__id
    WHERE (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
      AND (coverage_created_at_end IS NULL OR coverage.created_at <= coverage_created_at_end)
),
CoverageByTests AS (
    WITH MergedCoverage AS (
        SELECT
            Methods.signature,
            Methods.body_checksum,
            Coverage.test_id,
            definitions.name as test_name,
            definitions.runner as test_runner,
--            Tests.result, -- TODO include result once test-id-launch mapping is implemented
            BIT_OR(SUBSTRING(Coverage.probes FROM Methods.probe_start_pos + 1 FOR Methods.probes_count)) AS probes
        FROM Coverage
                            -- matching coverage to test-definition-id is wrong
                            -- that way, we can't individual test properties (e.g. result - passed/failed)
                            -- TODO: fix after "coverage - to - test launch id" mapping is implemented in autotest & java agents
        JOIN Methods ON Methods.classname = Coverage.classname
        LEFT JOIN raw_data.test_launches launches ON launches.id = Coverage.test_id -- left join to avoid loosing test coverage w/o metadata available
        LEFT JOIN raw_data.test_definitions definitions on definitions.id = launches.test_definition_id
        GROUP BY
            Methods.signature,
            Methods.body_checksum,
            Coverage.test_id,
            definitions.name,
            definitions.runner
    )
    SELECT *
    FROM MergedCoverage
    WHERE BIT_COUNT(probes) > 0
),
CoverageByMethods AS (
    SELECT
        CoverageByTests.signature,
        CoverageByTests.body_checksum,
        ARRAY_AGG(DISTINCT(CoverageByTests.test_id)) AS associated_test_definition_ids,
        ARRAY_AGG(DISTINCT(CoverageByTests.test_name)) AS associated_test_names,
        ARRAY_AGG(DISTINCT(CoverageByTests.test_runner)) AS associated_test_runners,
--        ARRAY_AGG(DISTINCT(CoverageByTests.test_result)) AS associated_test_results
        BIT_OR(CoverageByTests.probes) as merged_probes
    FROM CoverageByTests
    GROUP BY
        CoverageByTests.signature,
        CoverageByTests.body_checksum
)
SELECT
    Methods.build_id,
    Methods.name,
    Methods.params,
    Methods.return_type,
    Methods.classname,
    Methods.body_checksum,
    Methods.signature,
    Methods.probes_count,
    CoverageByMethods.merged_probes,
    COALESCE(CAST(BIT_COUNT(CoverageByMethods.merged_probes) AS INT), 0) AS covered_probes,
    COALESCE(CAST(BIT_COUNT(CoverageByMethods.merged_probes) AS FLOAT) / Methods.probes_count, 0.0) AS probes_coverage_ratio,
    CoverageByMethods.associated_test_definition_ids,
    CoverageByMethods.associated_test_names,
    CoverageByMethods.associated_test_runners
--    MethodsCoverage.associated_test_results
FROM Methods
LEFT JOIN CoverageByMethods ON Methods.signature = CoverageByMethods.signature AND Methods.body_checksum = CoverageByMethods.body_checksum;
END;
$$ LANGUAGE plpgsql;

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
CREATE OR REPLACE FUNCTION raw_data.get_accumulated_coverage_by_risks(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR
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
    BaselineMethods AS (
        SELECT * FROM raw_data.get_methods(input_baseline_build_id)
    ),
    Methods AS (
        SELECT * FROM raw_data.get_methods(input_build_id)
    ),
    Risks AS (
        WITH
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
        SELECT *, 'new' as risk_type FROM RisksNew
        UNION
        SELECT *, 'modified' as risk_type from RisksModified
    ),
    MatchingMethods AS (
        WITH
        SameGroupAndAppMethods AS (
            SELECT * FROM raw_data.get_same_group_and_app_methods(input_build_id)
        )
        SELECT
			Risks.classname,
			Risks.signature,
			Risks.body_checksum,
			Risks.risk_type,
			methods.build_id,
			methods.probe_start_pos,
			methods.probes_count
        FROM SameGroupAndAppMethods methods
        JOIN Risks ON
            Risks.body_checksum = methods.body_checksum
            AND Risks.signature = methods.signature
		ORDER BY Risks.body_checksum
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
            -- MatchingInstances.classname, -- TODO think about it
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
            MatchingCoverageByTest.body_checksum
    )
    SELECT
        Risks.risk_type,
        Risks.build_id,
        Risks.name,
        Risks.params,
        Risks.return_type,
        Risks.classname,
        Risks.body_checksum,
        Risks.signature,
        Risks.probes_count,
        MatchingCoverage.build_ids_coverage_source,
        MatchingCoverage.merged_probes,
		COALESCE(CAST(BIT_COUNT(MatchingCoverage.merged_probes) AS INT), 0),
		COALESCE(CAST(BIT_COUNT(MatchingCoverage.merged_probes) AS FLOAT) / Risks.probes_count, 0.0) AS probes_coverage_ratio,
        MatchingCoverage.associated_test_definition_ids
    FROM Risks
    LEFT JOIN MatchingCoverage ON Risks.body_checksum = MatchingCoverage.body_checksum
        AND Risks.signature = MatchingCoverage.signature
    ;
END;
$$ LANGUAGE plpgsql;



-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_risks(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR
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
	BaselineMethods AS (
		SELECT * FROM raw_data.get_methods(input_baseline_build_id)
	),
	Methods AS (
		SELECT * FROM raw_data.get_methods(input_build_id)
	),
	Risks AS (
		WITH
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
		SELECT *, 'new' as risk_type FROM RisksNew
		UNION
		SELECT *, 'modified' as risk_type from RisksModified
	),
	Instances AS (
		SELECT *
		FROM
			raw_data.get_instance_ids(input_build_id)
	),
	ClassesCoverage AS (
		SELECT coverage.*
		FROM raw_data.coverage coverage
		JOIN Instances ON coverage.instance_id = Instances.__id
	),
	RisksCoverage AS (
		WITH
		MethodsCoverage AS (
			SELECT
				Risks.risk_type,
				Risks.build_id,
				Risks.name,
				Risks.params,
				Risks.return_type,
				Risks.classname,
				Risks.body_checksum,
				Risks.signature,
				Risks.probes_count,
				SUBSTRING(ClassesCoverage.probes FROM Risks.probe_start_pos + 1 FOR Risks.probes_count) as probes,
				ClassesCoverage.test_id
			FROM Risks
			LEFT JOIN ClassesCoverage ON Risks.classname = ClassesCoverage.classname
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
		RisksCoverage.probes_count;
END;
$$ LANGUAGE plpgsql;



-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_recommended_tests(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR
) RETURNS TABLE (
    __test_definition_id VARCHAR,
    __test_type VARCHAR,
    __test_runner VARCHAR,
    __test_name VARCHAR,
    __test_path VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    WITH
    Risks AS (
        SELECT
            DISTINCT(UNNEST(rsk.__associated_test_definition_ids)) as __test_definition_id
        FROM
            raw_data.get_accumulated_coverage_by_risks(
                 input_build_id,
                 input_baseline_build_id
            ) rsk
    )
    SELECT DISTINCT
        Risks.__test_definition_id,
        tests.type,
        tests.runner,
        tests.name,
        tests.path
    FROM Risks
    -- TODO make it clear that some entries have no matching data in raw_data.test_definitions
    --      e.g. TEST_CONTEXT_NONE, or tests for which data is yet to be submitted
    LEFT JOIN raw_data.test_definitions test_definitions ON test_definitions.id = Risks.__test_definition_id
    -- TODO allow filtering by result (once mapping is implemented) WHERE tests.result = SUCCESS
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
CREATE OR REPLACE FUNCTION raw_data.get_accumulated_coverage_total_percent(
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
			raw_data.get_accumulated_coverage_by_methods(_input_build_id)
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
CREATE OR REPLACE FUNCTION raw_data.get_accumulated_coverage_by_methods(
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
        )
        SELECT
            MatchingInstances.signature,
            MatchingInstances.body_checksum,
            MatchingInstances.build_id as build_id_coverage_source,
            BIT_OR(SUBSTRING(coverage.probes FROM MatchingInstances.probe_start_pos + 1 FOR MatchingInstances.probes_count)) as merged_probes,
            coverage.test_id as test_id
        FROM raw_data.coverage coverage
        JOIN MatchingInstances ON MatchingInstances.instance_id = coverage.instance_id AND MatchingInstances.classname = coverage.classname
        JOIN TestLaunchIds ON coverage.test_id = TestLaunchIds.__id
        WHERE (coverage_created_at_start IS NULL OR coverage.created_at >= coverage_created_at_start)
              AND (coverage_created_at_end IS NULL OR coverage.created_at <= coverage_created_at_end)
        GROUP BY
            -- MatchingInstances.classname, -- TODO think about it
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
            MatchingCoverageByTest.body_checksum
    )
    SELECT
        Methods.build_id,
        Methods.name,
        Methods.params,
        Methods.return_type,
        Methods.classname,
        Methods.body_checksum,
        Methods.signature,
        Methods.probes_count,
        MatchingCoverage.build_ids_coverage_source,
        MatchingCoverage.merged_probes,
        COALESCE(CAST(BIT_COUNT(MatchingCoverage.merged_probes) AS INT), 0),
        COALESCE(CAST(BIT_COUNT(MatchingCoverage.merged_probes) AS FLOAT) / Methods.probes_count, 0.0) AS probes_coverage_ratio,
        MatchingCoverage.associated_test_definition_ids
    FROM Methods
    LEFT JOIN MatchingCoverage ON Methods.body_checksum = MatchingCoverage.body_checksum
        AND Methods.signature = MatchingCoverage.signature
;
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
    probes_count INT
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
        ;
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_instance_ids(input_build_id VARCHAR)
RETURNS TABLE (
    __id VARCHAR,
    __build_id VARCHAR,
    __created_at TIMESTAMP
)
AS $$
BEGIN
    RETURN QUERY
    SELECT *
    FROM raw_data.instances
    WHERE build_id = input_build_id;
END;
$$ LANGUAGE plpgsql;


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
    probes_count INT
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
    ;
END;
$$ LANGUAGE plpgsql;
