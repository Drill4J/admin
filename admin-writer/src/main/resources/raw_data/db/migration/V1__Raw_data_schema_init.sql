CREATE TABLE IF NOT EXISTS raw_data.instances (
    id VARCHAR PRIMARY KEY, -- uuid
    build_id VARCHAR -- builds.id
);

CREATE TABLE IF NOT EXISTS raw_data.builds (
    id VARCHAR PRIMARY KEY, -- build_id concatenated (by priority from top)
                            -- group_id, app_id, build_version
                            -- group_id, app_id, commit_sha
                            -- group_id, app_id, instance_id
    group_id VARCHAR,
    app_id VARCHAR,
    commit_sha VARCHAR,
    build_version VARCHAR,
    instance_id VARCHAR, -- instances.id

    -- none-identifying
    branch VARCHAR,
    commit_date VARCHAR,
    commit_message VARCHAR,
    commit_author VARCHAR
);

CREATE TABLE IF NOT EXISTS raw_data.methods (
    id VARCHAR PRIMARY KEY, -- build_id + signature
    build_id VARCHAR, -- builds.id
    classname VARCHAR,
    name VARCHAR,
    params VARCHAR,
    return_type VARCHAR,
    body_checksum VARCHAR,
    probe_start_pos INT,
    probes_count INT
);

CREATE TABLE IF NOT EXISTS raw_data.coverage (
    id SERIAL PRIMARY KEY,
    instance_id VARCHAR,  --> check in raw_data.instances, look up build_id, find methods
    classname VARCHAR,
    test_id VARCHAR, -- tests.id
    probes VARBIT
);

CREATE TABLE IF NOT EXISTS raw_data.tests (
    id VARCHAR PRIMARY KEY, -- uuid
    test_definition_id VARCHAR, -- combined from metadata from test runner (filename, suit, test name, parameters)
    name VARCHAR,
    result VARCHAR,
    test_agent_type VARCHAR
);
