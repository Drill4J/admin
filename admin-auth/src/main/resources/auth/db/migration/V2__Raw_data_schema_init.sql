-- AgentConfigTable
--CREATE TABLE IF NOT EXISTS test2code.agent_config (
CREATE TABLE IF NOT EXISTS auth.agent_config (
    id SERIAL PRIMARY KEY,
    agent_id VARCHAR(255),
    instance_id VARCHAR(255),
    service_group_id VARCHAR(255),
    build_version VARCHAR(255),
    agent_type VARCHAR(255),
    agent_version VARCHAR(255)
);

-- AstMethodTable
--CREATE TABLE IF NOT EXISTS test2code.ast_method (
CREATE TABLE IF NOT EXISTS auth.ast_method (
    id SERIAL PRIMARY KEY,
    instance_id VARCHAR(255),
    class_name VARCHAR(65535),
    name VARCHAR(65535),
    params VARCHAR(65535),
    return_type VARCHAR(65535),
    probe_start_pos INT,
    probes_count INT
);

-- ExecClassDataTable
--CREATE TABLE IF NOT EXISTS test2code.exec_class_data (
CREATE TABLE IF NOT EXISTS auth.exec_class_data (
    id SERIAL PRIMARY KEY,
    instance_id VARCHAR(255),
    class_name VARCHAR(65535),
    test_id VARCHAR(255),
    probes VARBIT
);
