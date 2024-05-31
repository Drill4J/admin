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
-- TODO add group_id and app_id columns to methods table? Tbd
-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_build_risks_accumulated_coverage(
	input_build_id VARCHAR,
    input_baseline_build_id VARCHAR
) RETURNS TABLE (
    _risk_type TEXT,
    _build_id VARCHAR,
    _name VARCHAR,
    _classname VARCHAR,
    _body_checksum VARCHAR,
    _signature VARCHAR,
    _probes_count INT,
    _build_ids_coverage_source VARCHAR ARRAY,
    _merged_probes BIT,
    _probes_coverage_ratio FLOAT,
    _associated_test_definition_ids VARCHAR ARRAY
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
        SELECT
			Risks.classname,
			Risks.signature,
			Risks.body_checksum,
			Risks.risk_type,
			methods.build_id,
			methods.probe_start_pos,
			methods.probes_count
        FROM raw_data.methods methods
        -- TODO - JOIN on raw_data.builds to get data on app_id, group_id
		-- + add WHERE to exclude unrelated methods
        JOIN Risks ON
            Risks.body_checksum = methods.body_checksum
            AND Risks.signature = methods.signature
    		-- TODO -- AND Risks.group_id = methods.group_id AND Risks.app_id = methods.app_id
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
	MatchingCoverage AS (
		SELECT
			-- group_id
			-- app_id
			MatchingInstances.signature,
			MatchingInstances.body_checksum,
			ARRAY_AGG(DISTINCT(MatchingInstances.build_id)) as build_ids_coverage_source,
			BIT_OR(SUBSTRING(coverage.probes FROM MatchingInstances.probe_start_pos + 1 FOR MatchingInstances.probes_count)) as merged_probes,
			ARRAY_AGG(DISTINCT(coverage.test_id)) as associated_test_definition_ids
		FROM raw_data.coverage coverage
		JOIN MatchingInstances ON MatchingInstances.instance_id = coverage.instance_id
		GROUP BY
			MatchingInstances.signature,
			MatchingInstances.body_checksum
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
		CAST(BIT_COUNT(MatchingCoverage.merged_probes) AS FLOAT) / Risks.probes_count AS probes_coverage_ratio,
        MatchingCoverage.associated_test_definition_ids
    FROM Risks
    LEFT JOIN MatchingCoverage ON Risks.body_checksum = MatchingCoverage.body_checksum
        AND Risks.signature = MatchingCoverage.signature
    ;
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------
-- TODO calling this fn and get_build_risks_accumulated_coverage performs same work twice
--      think of how we can avoid that

-- TODO come up with a better way to avoid column naming conflicts than adding _ and __
-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.get_recommended_tests(
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
    __probes_coverage_ratio FLOAT,
    __associated_test_definition_ids VARCHAR,
    __id INT,
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
    		_risk_type,
    		_build_id,
    		_name,
    		_classname,
    		_body_checksum,
    		_signature,
    		_probes_count,
    		_build_ids_coverage_source,
    		_merged_probes,
    		_probes_coverage_ratio,
    		UNNEST(_associated_test_definition_ids) as test_definition_id
    	FROM
    		raw_data.get_build_risks_accumulated_coverage(
                input_build_id,
                input_baseline_build_id
    		)
    )
    SELECT *
    FROM Risks
    LEFT JOIN raw_data.tests tests ON tests.test_definition_id = Risks.test_definition_id;
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.generate_build_id(
    in_group_id VARCHAR,
    in_app_id VARCHAR,
    in_instance_id VARCHAR DEFAULT NULL,
    in_commit_sha VARCHAR DEFAULT NULL,
    in_build_version VARCHAR DEFAULT NULL
) RETURNS VARCHAR AS $$
DECLARE
    build_id_elements VARCHAR;
BEGIN
    IF (LENGTH(in_group_id) = 0) THEN
        RAISE EXCEPTION 'groupId cannot be empty or blank';
    END IF;
    IF (LENGTH(in_app_id) = 0) THEN
        RAISE EXCEPTION 'appId cannot be empty or blank';
    END IF;
    IF (in_instance_id IS NULL AND in_commit_sha IS NULL AND in_build_version IS NULL) THEN
        RAISE EXCEPTION 'Provide at least one of the following: instance_id, commit_sha or build_version';
    END IF;

    build_id_elements := CONCAT(in_group_id, ':', in_app_id, ':', COALESCE(NULLIF(in_build_version, ''), NULLIF(in_commit_sha, ''), NULLIF(in_instance_id, '')));

    RETURN build_id_elements;
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE FUNCTION raw_data.check_build_exists(
    in_group_id VARCHAR,
    in_app_id VARCHAR,
    in_instance_id VARCHAR DEFAULT NULL,
    in_commit_sha VARCHAR DEFAULT NULL,
    in_build_version VARCHAR DEFAULT NULL
) RETURNS BOOLEAN AS $$
DECLARE
    build_id_to_check VARCHAR;
    build_exists BOOLEAN;
BEGIN
    build_id_to_check := raw_data.generate_build_id(in_group_id, in_app_id, in_instance_id, in_commit_sha, in_build_version);

    SELECT EXISTS(
        SELECT 1
		FROM raw_data.builds builds
        WHERE builds.id = build_id_to_check
    ) INTO build_exists;

    RETURN build_exists;
END;
$$ LANGUAGE plpgsql;
