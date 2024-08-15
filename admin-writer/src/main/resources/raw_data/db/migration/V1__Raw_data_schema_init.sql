CREATE TABLE IF NOT EXISTS raw_data.instances (
    id VARCHAR PRIMARY KEY, -- uuid
    build_id VARCHAR, -- builds.id
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
    commit_author VARCHAR,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS raw_data.methods (
    id VARCHAR PRIMARY KEY, -- build_id + signature
    build_id VARCHAR, -- builds.id
    classname VARCHAR,
    name VARCHAR,
    params VARCHAR,
    return_type VARCHAR,
    body_checksum VARCHAR,
    signature VARCHAR,
    probe_start_pos INT,
    probes_count INT
);

CREATE TABLE IF NOT EXISTS raw_data.coverage (
    id SERIAL PRIMARY KEY,
    instance_id VARCHAR,  --> check in raw_data.instances, look up build_id, find methods
    classname VARCHAR,
    test_id VARCHAR, -- TODO this is tests.test_definition_id. We want it replaced with test.id to trace unique launches
    probes VARBIT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS raw_data.test_launches (
    id VARCHAR PRIMARY KEY,
    group_id VARCHAR,
    test_definition_id VARCHAR, -- hash of the value combined from metadata from test runner (filename, suit, test name, parameters)
    test_task_id VARCHAR NULL,
    result VARCHAR NULL,
    -- Q: does the upsert update this field? A: No
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS raw_data.test_definitions (
    id VARCHAR PRIMARY KEY,
    group_id VARCHAR,
    type VARCHAR NULL,
    runner VARCHAR NULL,
    name VARCHAR NULL,
    path VARCHAR NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
