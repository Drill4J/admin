CREATE TABLE IF NOT EXISTS raw_data.agent_config (
    id SERIAL PRIMARY KEY,
    agent_id VARCHAR(255),
    instance_id VARCHAR(255),
    service_group_id VARCHAR(255),
    build_version VARCHAR(255),
    agent_type VARCHAR(255),
    agent_version VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS raw_data.ast_method (
    id SERIAL PRIMARY KEY,
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
    instance_id VARCHAR(255),
    class_name VARCHAR(65535),
    test_id VARCHAR(255),
    probes VARBIT
);

CREATE TABLE IF NOT EXISTS raw_data.test_metadata (
    id SERIAL PRIMARY KEY,
    test_id VARCHAR(255),
    type VARCHAR(255),
    name VARCHAR(2000)
);

CREATE OR REPLACE FUNCTION get_instance_ids_by_branch(current_hash TEXT, current_branch TEXT, base_branch TEXT, max_depth INT)
RETURNS TABLE (hash TEXT, parents TEXT, branch TEXT, entry_type TEXT, output_instance_id VARCHAR)
AS $$
BEGIN
    RETURN QUERY
    WITH RECURSIVE cte AS (
        SELECT
            id,
            instance_id,
            vcs_metadata ->> 'hash' AS hash,
            vcs_metadata ->> 'parents' AS parents,
            vcs_metadata ->> 'branch' AS branch,
            1 AS depth,
            0 AS diff_branch,
            false AS stop
        FROM
            raw_data.agent_config
        WHERE
            vcs_metadata ->> 'hash' = current_hash
            AND vcs_metadata ->> 'branch' = ANY(ARRAY[current_branch, base_branch])
        UNION ALL
        SELECT
            t.id,
            t.instance_id,
            t.vcs_metadata ->> 'hash' AS hash,
            t.vcs_metadata ->> 'parents' AS parents,
            t.vcs_metadata ->> 'branch' AS branch,
            cte.depth + 1 AS depth,
            CASE
                WHEN cte.diff_branch = 1 THEN 1
                WHEN t.vcs_metadata ->> 'branch' != current_branch THEN 1
                ELSE 0
            END AS diff_branch,
            CASE
                WHEN cte.diff_branch = 1 OR t.vcs_metadata ->> 'branch' != current_branch THEN true
                ELSE false
            END AS stop
        FROM
            raw_data.agent_config t
            JOIN cte ON t.vcs_metadata ->> 'hash' = ANY(string_to_array(cte.parents, ' '))
        WHERE
            t.vcs_metadata ->> 'branch' = ANY(ARRAY[current_branch, base_branch])
            AND cte.depth < max_depth
            AND NOT cte.stop
    ),
    Intermediate AS (
        SELECT DISTINCT
            cte.hash,
            cte.parents,
            cte.branch,
            CASE
                WHEN cte.hash = current_hash AND cte.branch = current_branch THEN 'current'
                WHEN cte.branch = current_branch THEN 'intermediate'
                WHEN cte.branch = base_branch THEN 'baseline'
                ELSE 'error_unknown_instance_encountered'
            END AS entry_type,
            cte.instance_id as instance_id
        FROM
            cte
    )
    SELECT
        Intermediate.hash,
        Intermediate.parents,
        Intermediate.branch,
        Intermediate.entry_type,
        Intermediate.instance_id
    FROM
        Intermediate;
END;
$$ LANGUAGE plpgsql;

