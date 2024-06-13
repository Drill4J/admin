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
CREATE OR REPLACE FUNCTION raw_data.get_total_coverage_percent(
    _build_id VARCHAR
)
RETURNS FLOAT AS $$
BEGIN
RETURN (
    WITH
    InstanceIds AS (
        SELECT id
        FROM raw_data.instances
        WHERE
            build_id = _build_id
    ),
    Methods AS (
        SELECT
            methods.signature,
            methods.classname,
            methods.name,
            methods.probe_start_pos,
            methods.probes_count
        FROM raw_data.methods methods
        WHERE methods.build_id = _build_id
            AND methods.probes_count > 0
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
        JOIN InstanceIds ON coverage.instance_id = InstanceIds.id
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
        SELECT id
        FROM raw_data.instances
        WHERE
            build_id = _build_id
    ),
    Classnames AS (
        SELECT classname
		  -- !warning! one cannot simply do SUM(methods.probes_count) to get class probe count - bc it'll aggregate dup entries from different instances
        FROM raw_data.methods methods
        WHERE methods.build_id = _build_id
            AND methods.probes_count > 0
	  		--  AND methods.classname LIKE CONCAT({{package_filter}, '%') -- filter by package name
        GROUP BY classname
    ),
    Coverage AS (
        SELECT
            coverage.classname,
            BIT_OR(coverage.probes) AS merged_probes
        FROM raw_data.coverage coverage
        JOIN InstanceIds ON coverage.instance_id = InstanceIds.id
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
CREATE OR REPLACE FUNCTION raw_data.get_coverage_by_methods(
    _build_id VARCHAR
) RETURNS TABLE (
    _classname VARCHAR,
    _method_name VARCHAR,
    _params VARCHAR,
    _return_type VARCHAR,
    _coverage_percentage FLOAT
) AS $$
BEGIN
    RETURN QUERY
    WITH
    InstanceIds AS (
        SELECT id
        FROM raw_data.instances
        WHERE
            build_id = _build_id
    ),
    Methods AS (
        SELECT
            methods.classname,
            methods.name,
            methods.params,
            methods.return_type,
            methods.probe_start_pos,
            methods.probes_count
        FROM raw_data.methods methods
        WHERE methods.build_id = _build_id
            AND methods.probes_count > 0
    ),
    Coverage AS (
        SELECT
            coverage.classname,
            BIT_OR(coverage.probes) AS merged_probes
        FROM raw_data.coverage coverage
        JOIN InstanceIds ON coverage.instance_id = InstanceIds.id
        GROUP BY coverage.classname
    )
    SELECT
        Methods.classname,
        Methods.name,
        Methods.params,
        Methods.return_type,
        CAST(COALESCE((
            BIT_COUNT(SUBSTRING(Coverage.merged_probes FROM Methods.probe_start_pos + 1 FOR Methods.probes_count)) * 100.0
            /
            Methods.probes_count
        ), 0.0) AS FLOAT) AS coverage_percent
    FROM Methods
    LEFT JOIN Coverage ON Methods.classname = Coverage.classname
    -- [[WHERE UPPER(Methods.classname) LIKE UPPER(CONCAT({{classname}, '%'))]] -- filter by class name
    ORDER BY
        Methods.classname,
        Methods.probe_start_pos,
        coverage_percent
    DESC;

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
        SELECT id
        FROM raw_data.instances
        WHERE
            build_id = _build_id
    ),
    Methods AS (
        SELECT
            methods.signature,
            methods.classname,
            methods.name,
            methods.probe_start_pos,
            methods.probes_count
        FROM raw_data.methods methods
        WHERE methods.build_id = _build_id
            AND methods.probes_count > 0
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
        JOIN InstanceIds ON coverage.instance_id = InstanceIds.id
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
CREATE OR REPLACE FUNCTION raw_data.get_build_risks_accumulated_coverage(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR
) RETURNS TABLE (
    __risk_type TEXT,
    __build_id VARCHAR,
    __name VARCHAR,
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
        SELECT
            methods.build_id,
            methods.signature,
            methods.classname,
            methods.name,
            methods.probe_start_pos,
            methods.probes_count,
            methods.body_checksum
        FROM raw_data.methods methods
        WHERE methods.build_id = input_baseline_build_id
            AND methods.probes_count > 0
    ),
    Methods AS (
        SELECT
            methods.build_id,
            methods.signature,
            methods.classname,
            methods.name,
            methods.probe_start_pos,
            methods.probes_count,
            methods.body_checksum
        FROM raw_data.methods methods
        WHERE methods.build_id = input_build_id
            AND methods.probes_count > 0
    ),
    Risks AS (
        WITH
        RisksModified AS (
            SELECT
                build_id,
                name,
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
            -- TODO add test to ensure this works correctly and indeed filters out methods from other builds
            SELECT methods.*
            FROM raw_data.builds l
            JOIN raw_data.builds r ON
            		r.group_id = l.group_id
            	AND r.app_id = l.app_id
            JOIN raw_data.methods methods ON methods.build_id = r.id
            WHERE l.id = input_build_id AND methods.probes_count > 0
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
            ARRAY_AGG(MatchingCoverageByTest.build_id_coverage_source) as build_ids_coverage_source,
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
CREATE OR REPLACE FUNCTION raw_data.get_recommended_tests(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR
) RETURNS TABLE (
    __test_id INT,
    __test_definition_id VARCHAR,
    __test_type VARCHAR,
    __test_runner VARCHAR,
    __test_name VARCHAR,
    __test_path VARCHAR,
    __test_result VARCHAR,
    __test_created_at TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    WITH
    Risks AS (
        SELECT
            DISTINCT(UNNEST(rsk.__associated_test_definition_ids)) as __test_definition_id
        FROM
            raw_data.get_build_risks_accumulated_coverage(
                 input_build_id,
                 input_baseline_build_id
            ) rsk
    )
    SELECT tests.*
    FROM Risks
    LEFT JOIN raw_data.tests tests ON tests.test_definition_id = Risks.__test_definition_id;
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
