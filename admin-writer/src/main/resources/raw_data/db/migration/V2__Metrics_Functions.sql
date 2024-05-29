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
            CONCAT(methods.classname, ', ', methods.name, ', ', methods.params, ', ', methods.return_type) AS signature,
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
            CONCAT(methods.classname, ', ', methods.name, ', ', methods.params, ', ', methods.return_type) AS signature,
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
