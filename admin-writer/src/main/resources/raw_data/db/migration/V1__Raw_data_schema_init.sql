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
