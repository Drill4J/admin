CREATE TABLE IF NOT EXISTS raw_data.agent_config (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    agent_id VARCHAR(255),
    instance_id VARCHAR(255),
    service_group_id VARCHAR(255),
    build_version VARCHAR(255),
    agent_type VARCHAR(255),
    agent_version VARCHAR(255),
    vcs_metadata_hash VARCHAR(255),
    vcs_metadata_parents VARCHAR(255),
    vcs_metadata_branch VARCHAR(255);
);

CREATE TABLE IF NOT EXISTS raw_data.ast_method (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    instance_id VARCHAR(255),
    class_name VARCHAR(65535),
    name VARCHAR(65535),
    params VARCHAR(65535),
    return_type VARCHAR(65535),
    body_checksum VARCHAR(20), -- crc64 stringified hash
    probe_start_pos INT,
    probes_count INT
);

CREATE TABLE IF NOT EXISTS raw_data.exec_class_data (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    instance_id VARCHAR(255),
    class_name VARCHAR(65535),
    test_id VARCHAR(255),
    probes VARBIT
);

CREATE TABLE IF NOT EXISTS raw_data.test_metadata (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    test_id VARCHAR(255),
    type VARCHAR(255),
    name VARCHAR(2000)
);

CREATE OR REPLACE FUNCTION get_instance_ids_by_branch(_agent_id VARCHAR, _group_id VARCHAR, current_hash VARCHAR, current_branch VARCHAR, base_branch VARCHAR, max_depth INT)
RETURNS TABLE (agent_id VARCHAR, service_group_id VARCHAR, hash VARCHAR, parents VARCHAR, branch VARCHAR, entry_type VARCHAR, instance_id VARCHAR)
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
                WHEN cte.diff_branch = 1 OR t.vcs_metadata_branch != current_branch THEN true
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
				WHEN cte.branch = current_branch THEN 'intermediate'
				WHEN cte.branch = base_branch THEN 'baseline'
				ELSE 'error_unknown_instance_encountered'
			END AS VARCHAR
		) AS entry_type,
		cte.instance_id
	FROM cte;
END;
$$ LANGUAGE plpgsql;