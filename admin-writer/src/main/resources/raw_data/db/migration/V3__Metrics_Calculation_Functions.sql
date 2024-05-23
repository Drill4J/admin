-- Metrics-related functions

--------------------------------------------------------

CREATE OR REPLACE FUNCTION calculate_total_coverage_percent(
    _group_id VARCHAR DEFAULT NULL,
    _app_id VARCHAR DEFAULT NULL,
    _commit_sha VARCHAR DEFAULT NULL,
    _instance_id VARCHAR DEFAULT NULL
)
RETURNS FLOAT AS $$
BEGIN
RETURN (
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.instances
        WHERE
            (group_id = _group_id
                AND app_id = _app_id
                AND commit_sha = _commit_sha)
            OR
            instance_id = _instance_id
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.classname, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.classname,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.methods am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    BuildClasses AS (
        SELECT
            classname,
            SUM(BuildMethods.probes_count) as probes_count
        FROM BuildMethods
        GROUP BY classname
    ),
    CoveredClasses AS (
        SELECT
            classname,
            BIT_COUNT(BIT_OR(probes)) AS set_bits_count
        FROM
            raw_data.coverage ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY
            classname
    )
    SELECT
        COALESCE(SUM(CoveredClasses.set_bits_count) / SUM(BuildClasses.probes_count), 0) as total_coverage
    FROM BuildClasses
    LEFT JOIN CoveredClasses ON BuildClasses.classname = CoveredClasses.classname
    );
END;
$$ LANGUAGE plpgsql;

--------------------------------------------------------


--------------------------------------------------------

CREATE OR REPLACE FUNCTION get_coverage_by_classes(
    _group_id VARCHAR DEFAULT NULL,
    _app_id VARCHAR DEFAULT NULL,
    _commit_sha VARCHAR DEFAULT NULL,
    _instance_id VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    _classname varchar,
    _merged_probes bit
) AS $$
BEGIN
    RETURN QUERY
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.instances
        WHERE
            (group_id = _group_id
                AND app_id = _app_id
                AND commit_sha = _commit_sha)
            OR
            instance_id = _instance_id
    ),
    BuildClassNames AS (
        SELECT classname
		  -- !warning! one cannot simply do SUM(am.probes_count) to get class probe count - bc it'll aggregate dup entries from different instances
        FROM raw_data.methods am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE probes_count > 0
	  		--  AND am.classname LIKE CONCAT({{package_filter}, '%') -- filter by package name
        GROUP BY classname
    ),
    CoverageData AS (
        SELECT
            ecd.classname,
            BIT_OR(ecd.probes) AS merged_probes
        FROM raw_data.coverage ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.classname
    ),
    CoverageByClasses AS (
        SELECT
            BuildClassNames.classname,
            CoverageData.merged_probes
        FROM BuildClassNames
        LEFT JOIN CoverageData ON BuildClassNames.classname = CoverageData.classname
    )
    SELECT *
    FROM CoverageByClasses;

END;
$$ LANGUAGE plpgsql;



--------------------------------------------------------


--------------------------------------------------------

CREATE OR REPLACE FUNCTION get_coverage_by_methods(
    _group_id VARCHAR DEFAULT NULL,
    _app_id VARCHAR DEFAULT NULL,
    _commit_sha VARCHAR DEFAULT NULL,
    _instance_id VARCHAR DEFAULT NULL
) RETURNS TABLE (
    _classname VARCHAR,
    _method_name VARCHAR,
    _coverage_percentage FLOAT,
    _params VARCHAR,
    _return_type VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.instances
        WHERE
            (group_id = _group_id
                AND app_id = _app_id
                AND commit_sha = _commit_sha)
            OR
            instance_id = _instance_id
    ),
    BuildMethods AS (
        SELECT DISTINCT
            am.classname,
            am.name,
            am.params,
            am.return_type,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.methods am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    CoverageData AS (
        SELECT
            ecd.classname,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.coverage ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.classname
    )
    SELECT
        BuildMethods.classname,
        BuildMethods.name,
        CAST(COALESCE((
            BIT_COUNT(SUBSTRING(CoverageData.or_result FROM BuildMethods.probe_start_pos + 1 FOR BuildMethods.probes_count)) * 100.0
            /
            BuildMethods.probes_count
        ), 0.0) AS FLOAT) AS coverage_percent,
        BuildMethods.params,
        BuildMethods.return_type
    FROM BuildMethods
    LEFT JOIN CoverageData ON BuildMethods.classname = CoverageData.classname
    -- [[WHERE UPPER(BuildMethods.classname) LIKE UPPER(CONCAT({{classname}, '%'))]] -- filter by class name
    ORDER BY
        BuildMethods.classname,
        BuildMethods.probe_start_pos,
        coverage_percent
    DESC;

END;
$$ LANGUAGE plpgsql;

--------------------------------------------------------


--------------------------------------------------------

CREATE OR REPLACE FUNCTION get_coverage_by_packages(
    _group_id VARCHAR DEFAULT NULL,
    _app_id VARCHAR DEFAULT NULL,
    _commit_sha VARCHAR DEFAULT NULL,
    _instance_id VARCHAR DEFAULT NULL
)
RETURNS TABLE(package_name VARCHAR, coverage_percentage FLOAT) AS $$
BEGIN
    RETURN QUERY
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.instances
        WHERE
            (group_id = _group_id
                AND app_id = _app_id
                AND commit_sha = _commit_sha)
            OR
            instance_id = _instance_id
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.classname, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.classname,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.methods am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    BuildClasses AS (
        SELECT
            classname,
            SUM(BuildMethods.probes_count) as probes_count
        FROM BuildMethods
        GROUP BY classname
    ),
    BuildPackages AS (
        SELECT
            REVERSE(
                SUBSTRING(
                    REVERSE(BuildClasses.classname) FROM POSITION('/' IN REVERSE(BuildClasses.classname)) + 1)) package_name,
            SUM(BuildClasses.probes_count) as package_probes_count
        FROM BuildClasses
        GROUP BY package_name
    ),
    CoveredClasses AS (
        SELECT
            REVERSE(
                SUBSTRING(
                    REVERSE(classname) FROM POSITION('/' IN REVERSE(classname)) + 1)) AS package_name,
            classname,
            BIT_COUNT(BIT_OR(probes)) AS set_bits_count
        FROM
            raw_data.coverage ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY
            classname, package_name
    )
    SELECT
        CAST(BuildPackages.package_name AS VARCHAR) as package_name,
        CAST(COALESCE(
            (SUM(CoveredClasses.set_bits_count) / BuildPackages.package_probes_count) * 100.0
        , 0) AS FLOAT) AS coverage_percentage
    FROM BuildPackages
    LEFT JOIN CoveredClasses ON BuildPackages.package_name = CoveredClasses.package_name
    GROUP BY BuildPackages.package_name,
            BuildPackages.package_probes_count
    ORDER BY
        coverage_percentage DESC;

END;
$$ LANGUAGE plpgsql;

--------------------------------------------------------


--------------------------------------------------------

CREATE OR REPLACE FUNCTION get_risks_by_branch_diff(
	_group_id VARCHAR,
	_app_id VARCHAR,
    _commit_sha VARCHAR,
    _current_vcs_ref VARCHAR, -- _commit_sha VARCHAR,
    _base_vcs_ref VARCHAR DEFAULT '' --     _baseline_commit_sha VARCHAR
) RETURNS TABLE (
    _classname VARCHAR,
    _name VARCHAR,
    _risk_type TEXT,
    _covered_in_version VARCHAR,
    _coverage_percentage FLOAT
) AS $$
BEGIN
    RETURN QUERY
    BaselineInstanceIds AS (
        SELECT DISTINCT
            instance_id,
            commit_sha
        FROM raw_data.deployments
        RIGHT JOIN raw_data.builds ON

    ),
    BaselineMethods AS (
        SELECT DISTINCT
            CONCAT(am.classname, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.classname,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum,
            BaselineInstanceIds.build_version
        FROM raw_data.methods am
        JOIN BaselineInstanceIds ON am.instance_id = BaselineInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    ChildrenInstanceIds AS (
        SELECT DISTINCT
            instance_id,
            build_version
        FROM InstanceIds
        WHERE
            entry_type = 'intermediate'
            OR
            entry_type = 'current'
    ),
    ChildrenMethods AS (
        SELECT DISTINCT
            CONCAT(am.classname, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.classname,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum,
            ChildrenInstanceIds.build_version
        FROM raw_data.methods am
        JOIN ChildrenInstanceIds ON am.instance_id = ChildrenInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    ChildrenRisks AS (
        WITH
        RisksModified AS (
            SELECT
                build_version,
                name,
                classname,
                body_checksum,
                signature,
                probe_start_pos,
                probes_count
            FROM ChildrenMethods AS q2
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
                build_version,
                name,
                classname,
                body_checksum,
                signature,
                probe_start_pos,
                probes_count
            FROM ChildrenMethods AS q2
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
    LatestRisks AS (
        SELECT *
        FROM ChildrenRisks
        WHERE build_version = _current_vcs_ref
    ),
    IntermediateRisks AS (
        SELECT *
        FROM ChildrenRisks
        WHERE build_version <> _current_vcs_ref
    ),
    PreviousRisks AS (
        SELECT DISTINCT
            LatestRisks.classname,
            LatestRisks.name,
            LatestRisks.signature,
            LatestRisks.body_checksum,
            LatestRisks.probe_start_pos,
            LatestRisks.probes_count,
            LatestRisks.risk_type
        FROM IntermediateRisks
        JOIN LatestRisks ON
            LatestRisks.signature = IntermediateRisks.signature
            AND LatestRisks.body_checksum = IntermediateRisks.body_checksum
    ),
    ChildrenCoverage AS (
        SELECT
            ecd.classname,
            build_version,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.coverage ecd
        JOIN ChildrenInstanceIds ON ecd.instance_id = ChildrenInstanceIds.instance_id
        GROUP BY build_version, ecd.classname
    ),
    ChildrenRiskCoverage AS (
        SELECT
            rsk.classname,
            rsk.name,
            rsk.risk_type,
            cd.build_version as covered_in_version,
            CAST(COALESCE(
                (BIT_COUNT(SUBSTRING(cd.or_result FROM rsk.probe_start_pos + 1 FOR rsk.probes_count)) * 100.0 / rsk.probes_count)
                , 0) AS FLOAT) AS _coverage_percentage
        FROM PreviousRisks rsk
        LEFT JOIN ChildrenCoverage cd ON
            rsk.classname = cd.classname
            AND BIT_COUNT(SUBSTRING(cd.or_result FROM rsk.probe_start_pos + 1 FOR rsk.probes_count)) > 0
    )
    SELECT *
    FROM ChildrenRiskCoverage
    ORDER BY covered_in_version DESC;
END;
$$ LANGUAGE plpgsql;

--------------------------------------------------------


--------------------------------------------------------

CREATE OR REPLACE FUNCTION get_risks_by_branch_diff_only_own_coverage(
    _group_id VARCHAR,
    _app_id VARCHAR,
    _current_vcs_ref VARCHAR,
    _current_branch VARCHAR,
    _base_branch VARCHAR,
    _base_vcs_ref VARCHAR DEFAULT '')
RETURNS TABLE (classname VARCHAR, name VARCHAR, coverage_percentage FLOAT)
AS $$
BEGIN
    RETURN QUERY
    WITH
    InstanceIds AS (
        SELECT DISTINCT
            instance_id,
            hash as build_version,
            branch,
            entry_type
        FROM raw_data.get_instance_ids_by_branch(_app_id, _group_id, _current_vcs_ref, _current_branch, _base_branch, _base_vcs_ref)
    ),
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM InstanceIds
        WHERE entry_type = 'current'
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.classname, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.classname,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum
        FROM raw_data.methods am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    CoverageData AS (
        SELECT
            ecd.classname,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.coverage ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.classname
    ),
    Risks AS (
        WITH
        ParentBuildInstanceIds AS (
            SELECT DISTINCT instance_id
            FROM InstanceIds
            WHERE entry_type = 'baseline'
        ),
        ParentBuildMethods AS (
            SELECT DISTINCT
                CONCAT(am.classname, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
                am.classname,
                am.name,
                am.probe_start_pos,
                am.probes_count,
                am.body_checksum
            FROM raw_data.methods am
            JOIN ParentBuildInstanceIds ON am.instance_id = ParentBuildInstanceIds.instance_id
            WHERE am.probes_count > 0
        )
        SELECT *
        FROM BuildMethods AS q2
        WHERE
            -- modifed
            EXISTS (
                SELECT 1
                FROM ParentBuildMethods AS q1
                WHERE q1.signature = q2.signature
                AND q1.body_checksum <> q2.body_checksum
            )
            -- new
            OR NOT EXISTS (
                SELECT 1
                FROM ParentBuildMethods AS q1
                WHERE q1.signature = q2.signature
            )
    )
    SELECT
        rsk.classname,
        rsk.name,
        CAST(COALESCE(
            (BIT_COUNT(SUBSTRING(cd.or_result FROM rsk.probe_start_pos + 1 FOR rsk.probes_count)) * 100.0 / rsk.probes_count)
            , 0) AS FLOAT) AS coverage_percentage
    FROM Risks rsk
    LEFT JOIN CoverageData cd ON cd.classname = rsk.classname
    ORDER BY coverage_percentage, classname;

END;
$$ LANGUAGE plpgsql;