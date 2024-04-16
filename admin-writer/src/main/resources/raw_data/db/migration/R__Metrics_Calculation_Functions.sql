-- Metrics-related functions

CREATE OR REPLACE FUNCTION get_instance_ids_by_branch(
	_agent_id VARCHAR,
	_group_id VARCHAR,
	current_hash VARCHAR,
	current_branch VARCHAR,
	base_branch VARCHAR,
	base_hash VARCHAR DEFAULT '',
	max_depth INT DEFAULT 100)
RETURNS TABLE (
	agent_id VARCHAR,
	service_group_id VARCHAR,
	hash VARCHAR,
	parents VARCHAR,
	branch VARCHAR,
	entry_type VARCHAR,
	instance_id VARCHAR)
AS $$
BEGIN
    RETURN QUERY
    WITH RECURSIVE cte AS (
        SELECT
            ac.id,
			ac.agent_id,
			ac.service_group_id,
            ac.instance_id,
            ac.vcs_metadata_hash AS hash,
            ac.vcs_metadata_parents AS parents,
            ac.vcs_metadata_branch AS branch,
            1 AS depth,
            0 AS diff_branch,
            false AS stop
        FROM
            raw_data.agent_config ac
        WHERE
            ac.agent_id = _agent_id
            AND ac.service_group_id = _group_id
            AND ac.vcs_metadata_hash = current_hash
            AND ac.vcs_metadata_branch = ANY(ARRAY[current_branch, base_branch])
        UNION ALL
        SELECT
            t.id,
			t.agent_id,
			t.service_group_id,
            t.instance_id,
            t.vcs_metadata_hash AS hash,
            t.vcs_metadata_parents AS parents,
            t.vcs_metadata_branch AS branch,
            cte.depth + 1 AS depth,
            CASE
                WHEN cte.diff_branch = 1 THEN 1
                WHEN t.vcs_metadata_branch != current_branch THEN 1
                ELSE 0
            END AS diff_branch,
            CASE
                WHEN
					-- stop traversal if branch changed and we don't know base_hash
					((cte.diff_branch = 1 OR t.vcs_metadata_branch != current_branch) AND base_hash = '')
					-- stop traversal if we reached commit with base_hash
					OR t.vcs_metadata_hash = base_hash
					THEN true
                ELSE false
            END AS stop
        FROM
            raw_data.agent_config t
            JOIN cte ON t.vcs_metadata_hash = ANY(string_to_array(cte.parents, ' '))
        WHERE
            t.vcs_metadata_branch = ANY(ARRAY[current_branch, base_branch])
            AND cte.depth < max_depth
            AND NOT cte.stop
    )
	SELECT DISTINCT
		cte.agent_id,
		cte.service_group_id,
		cte.hash,
		cte.parents,
		cte.branch,
		CAST(
			CASE
				WHEN cte.hash = current_hash AND cte.branch = current_branch THEN 'current'

				WHEN
                    -- single branch + base_hash - pick commit with hash = base_hash
                    (current_branch = base_branch AND base_hash <> '' AND cte.hash = base_hash)
                    OR
                    -- different branches + base_hash - pick commit with hash = base_hash
                    (current_branch <> base_branch AND base_hash <> '' AND cte.hash = base_hash)
                    OR
                    -- different branches + no base_hash - pick first commit of base_branch
                    (current_branch <> base_branch AND base_hash = '' AND cte.branch = base_branch)
                    THEN 'baseline'

                ELSE 'intermediate'
			END AS VARCHAR
		) AS entry_type,
		cte.instance_id
	FROM cte;
END;

$$ LANGUAGE plpgsql;
--------------------------------------------------------


--------------------------------------------------------

CREATE OR REPLACE FUNCTION calculate_total_coverage_percent(
    _group_id VARCHAR,
    _agent_id VARCHAR,
    _vcs_metadata_hash VARCHAR
)
RETURNS FLOAT AS $$
BEGIN
RETURN (
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE service_group_id = _group_id
            AND agent_id = _agent_id
            AND vcs_metadata_hash = _vcs_metadata_hash
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    BuildClasses AS (
        SELECT
            class_name,
            SUM(BuildMethods.probes_count) as probes_count
        FROM BuildMethods
        GROUP BY class_name
    ),
    CoveredClasses AS (
        SELECT
            class_name,
            BIT_COUNT(BIT_OR(probes)) AS set_bits_count
        FROM
            raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY
            class_name
    )
    SELECT
        COALESCE(SUM(CoveredClasses.set_bits_count) / SUM(BuildClasses.probes_count), 0) as total_coverage
    FROM BuildClasses
    LEFT JOIN CoveredClasses ON BuildClasses.class_name = CoveredClasses.class_name
    );
END;
$$ LANGUAGE plpgsql;

--------------------------------------------------------


--------------------------------------------------------

CREATE OR REPLACE FUNCTION get_coverage_by_classes(
    IN _group_id varchar,
    IN _agent_id varchar,
    IN _vcs_metadata_hash varchar
)
RETURNS TABLE (
    _class_name varchar,
    _merged_probes bit
) AS $$
BEGIN
    RETURN QUERY
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE service_group_id = _group_id AND agent_id = _agent_id AND vcs_metadata_hash = _vcs_metadata_hash
    ),
    BuildClassNames AS (
        SELECT class_name
		  -- !warning! one cannot simply do SUM(am.probes_count) to get class probe count - bc it'll aggregate dup entries from different instances
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE probes_count > 0
	  		--  AND am.class_name LIKE CONCAT({{package_filter}, '%') -- filter by package name
        GROUP BY class_name
    ),
    CoverageData AS (
        SELECT
            ecd.class_name,
            BIT_OR(ecd.probes) AS merged_probes
        FROM raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.class_name
    ),
    CoverageByClasses AS (
        SELECT
            BuildClassNames.class_name,
            CoverageData.merged_probes
        FROM BuildClassNames
        LEFT JOIN CoverageData ON BuildClassNames.class_name = CoverageData.class_name
    )
    SELECT *
    FROM CoverageByClasses;

END;
$$ LANGUAGE plpgsql;



--------------------------------------------------------


--------------------------------------------------------

CREATE OR REPLACE FUNCTION get_coverage_by_methods(
	_group_id VARCHAR,
    _agent_id VARCHAR,
    _vcs_metadata_hash VARCHAR
) RETURNS TABLE (
    _class_name VARCHAR,
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
        FROM raw_data.agent_config
        WHERE service_group_id = _group_id AND agent_id = _agent_id AND vcs_metadata_hash = _vcs_metadata_hash
    ),
    BuildMethods AS (
        SELECT DISTINCT
            am.class_name,
            am.name,
            am.params,
            am.return_type,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    CoverageData AS (
        SELECT
            ecd.class_name,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.class_name
    )
    SELECT
        BuildMethods.class_name,
        BuildMethods.name,
        CAST(COALESCE((
            BIT_COUNT(SUBSTRING(CoverageData.or_result FROM BuildMethods.probe_start_pos + 1 FOR BuildMethods.probes_count)) * 100.0
            /
            BuildMethods.probes_count
        ), 0.0) AS FLOAT) AS coverage_percent,
        BuildMethods.params,
        BuildMethods.return_type
    FROM BuildMethods
    LEFT JOIN CoverageData ON BuildMethods.class_name = CoverageData.class_name
    -- [[WHERE UPPER(BuildMethods.class_name) LIKE UPPER(CONCAT({{class_name}, '%'))]] -- filter by class name
    ORDER BY
        BuildMethods.class_name,
        BuildMethods.probe_start_pos,
        coverage_percent
    DESC;

END;
$$ LANGUAGE plpgsql;

--------------------------------------------------------


--------------------------------------------------------

CREATE OR REPLACE FUNCTION get_coverage_by_packages(_group_id VARCHAR, _agent_id VARCHAR, _vcs_metadata_hash VARCHAR)
RETURNS TABLE(package_name VARCHAR, coverage_percentage FLOAT) AS $$
BEGIN
    RETURN QUERY
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE service_group_id = _group_id AND agent_id = _agent_id and vcs_metadata_hash = _vcs_metadata_hash
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    BuildClasses AS (
        SELECT
            class_name,
            SUM(BuildMethods.probes_count) as probes_count
        FROM BuildMethods
        GROUP BY class_name
    ),
    BuildPackages AS (
        SELECT
            REVERSE(
                SUBSTRING(
                    REVERSE(BuildClasses.class_name) FROM POSITION('/' IN REVERSE(BuildClasses.class_name)) + 1)) package_name,
            SUM(BuildClasses.probes_count) as package_probes_count
        FROM BuildClasses
        GROUP BY package_name
    ),
    CoveredClasses AS (
        SELECT
            REVERSE(
                SUBSTRING(
                    REVERSE(class_name) FROM POSITION('/' IN REVERSE(class_name)) + 1)) AS package_name,
            class_name,
            BIT_COUNT(BIT_OR(probes)) AS set_bits_count
        FROM
            raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY
            class_name, package_name
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
	_agent_id VARCHAR,
    _current_vcs_ref VARCHAR,
    _current_branch VARCHAR,
    _base_branch VARCHAR,
    _base_vcs_ref VARCHAR DEFAULT ''
) RETURNS TABLE (
    _class_name VARCHAR,
    _name VARCHAR,
    _risk_type TEXT,
    _covered_in_version VARCHAR,
    _coverage_percentage FLOAT
) AS $$
BEGIN
    RETURN QUERY
    WITH InstanceIds AS (
        SELECT DISTINCT
            instance_id,
            hash as build_version,
            branch,
            entry_type
        FROM get_instance_ids_by_branch(_agent_id, _group_id, _current_vcs_ref, _current_branch, _base_branch, _base_vcs_ref)
    ),
    BaselineInstanceIds AS (
        SELECT DISTINCT
            instance_id,
            build_version
        FROM InstanceIds
        WHERE entry_type = 'baseline'
    ),
    BaselineMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum,
            BaselineInstanceIds.build_version
        FROM raw_data.ast_method am
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
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum,
            ChildrenInstanceIds.build_version
        FROM raw_data.ast_method am
        JOIN ChildrenInstanceIds ON am.instance_id = ChildrenInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    ChildrenRisks AS (
        WITH
        RisksModified AS (
            SELECT
                build_version,
                name,
                class_name,
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
                class_name,
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
            LatestRisks.class_name,
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
            ecd.class_name,
            build_version,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.exec_class_data ecd
        JOIN ChildrenInstanceIds ON ecd.instance_id = ChildrenInstanceIds.instance_id
        GROUP BY build_version, ecd.class_name
    ),
    ChildrenRiskCoverage AS (
        SELECT
            rsk.class_name,
            rsk.name,
            rsk.risk_type,
            cd.build_version as covered_in_version,
            CAST(COALESCE(
                (BIT_COUNT(SUBSTRING(cd.or_result FROM rsk.probe_start_pos + 1 FOR rsk.probes_count)) * 100.0 / rsk.probes_count)
                , 0) AS FLOAT) AS _coverage_percentage
        FROM PreviousRisks rsk
        LEFT JOIN ChildrenCoverage cd ON
            rsk.class_name = cd.class_name
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
    _agent_id VARCHAR,
    _current_vcs_ref VARCHAR,
    _current_branch VARCHAR,
    _base_branch VARCHAR,
    _base_vcs_ref VARCHAR DEFAULT '')
RETURNS TABLE (class_name VARCHAR, name VARCHAR, coverage_percentage FLOAT)
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
        FROM get_instance_ids_by_branch(_agent_id, _group_id, _current_vcs_ref, _current_branch, _base_branch, _base_vcs_ref)
    ),
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM InstanceIds
        WHERE entry_type = 'current'
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    CoverageData AS (
        SELECT
            ecd.class_name,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.class_name
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
                CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
                am.class_name,
                am.name,
                am.probe_start_pos,
                am.probes_count,
                am.body_checksum
            FROM raw_data.ast_method am
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
        rsk.class_name,
        rsk.name,
        CAST(COALESCE(
            (BIT_COUNT(SUBSTRING(cd.or_result FROM rsk.probe_start_pos + 1 FOR rsk.probes_count)) * 100.0 / rsk.probes_count)
            , 0) AS FLOAT) AS coverage_percentage
    FROM Risks rsk
    LEFT JOIN CoverageData cd ON cd.class_name = rsk.class_name
    ORDER BY coverage_percentage, class_name;

END;
$$ LANGUAGE plpgsql;