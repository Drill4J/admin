CREATE TABLE IF NOT EXISTS metrics.builds_table (
    group_id VARCHAR,
    app_id VARCHAR,
    build_id VARCHAR,
    version_id VARCHAR,
    app_env_ids VARCHAR[],
    build_version VARCHAR,
    branch VARCHAR,
    commit_sha VARCHAR,
    commit_author VARCHAR,
    commit_message VARCHAR,
    committed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    updated_at_day TIMESTAMP WITHOUT TIME ZONE,
    PRIMARY KEY (group_id, app_id, build_id)
);
CREATE INDEX ON metrics.builds_table(group_id, updated_at_day);

CREATE TABLE IF NOT EXISTS metrics.methods_table (
    group_id VARCHAR,
    app_id VARCHAR,
    method_id VARCHAR(32),
    signature VARCHAR,
    method_name VARCHAR,
    class_name VARCHAR,
    method_params VARCHAR,
    return_type VARCHAR,
    body_checksum VARCHAR,
    probes_count INT,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    PRIMARY KEY (group_id, app_id, method_id)
);
CREATE INDEX ON metrics.methods_table(group_id, created_at_day);
CREATE INDEX ON metrics.methods_table (group_id, app_id, signature);

CREATE TABLE IF NOT EXISTS metrics.build_methods_table (
    group_id VARCHAR,
    app_id VARCHAR,
    build_id VARCHAR,
    method_id VARCHAR(32),
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    PRIMARY KEY (group_id, app_id, build_id, method_id)
);
CREATE INDEX ON metrics.build_methods_table(group_id, created_at_day);

CREATE TABLE IF NOT EXISTS metrics.test_launches_table (
    group_id VARCHAR,
    test_launch_id VARCHAR,
    test_definition_id VARCHAR,
    test_session_id VARCHAR,
    test_result VARCHAR,
    test_duration BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    passed INT,
    failed INT,
    skipped INT,
    smart_skipped INT,
    success INT,
    PRIMARY KEY (group_id, test_launch_id)
);
CREATE INDEX ON metrics.test_launches_table(group_id, created_at_day);
CREATE INDEX ON metrics.test_launches_table(group_id, test_definition_id);
CREATE INDEX ON metrics.test_launches_table(group_id, test_session_id);

CREATE TABLE IF NOT EXISTS metrics.test_definitions_table (
    group_id VARCHAR,
    test_definition_id VARCHAR,
    test_path VARCHAR,
    test_name VARCHAR,
    test_runner VARCHAR,
    test_tags VARCHAR[],
    test_metadata JSONB,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    updated_at_day TIMESTAMP WITHOUT TIME ZONE,
    PRIMARY KEY (group_id, test_definition_id)
);
CREATE INDEX ON metrics.test_definitions_table(group_id, updated_at_day);
CREATE INDEX ON metrics.test_definitions_table(group_id, test_path, test_name);

CREATE TABLE IF NOT EXISTS metrics.test_sessions_table (
    group_id VARCHAR,
    test_session_id VARCHAR,
    test_task_id VARCHAR,
    session_started_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    created_by VARCHAR,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    PRIMARY KEY (group_id, test_session_id)
);
CREATE INDEX ON metrics.test_sessions_table(group_id, created_at_day);

CREATE TABLE IF NOT EXISTS metrics.test_session_builds_table (
    group_id VARCHAR,
    app_id VARCHAR,
    build_id VARCHAR,
    test_session_id VARCHAR,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    updated_at_day TIMESTAMP WITHOUT TIME ZONE,
    PRIMARY KEY (group_id, app_id, build_id, test_session_id)
);
CREATE INDEX ON metrics.test_session_builds_table(group_id, updated_at_day);
CREATE INDEX ON metrics.test_session_builds_table(group_id, test_session_id);

CREATE TABLE IF NOT EXISTS metrics.build_method_test_definition_coverage_table (
    group_id VARCHAR,
    app_id VARCHAR,
    build_id VARCHAR,
    method_id VARCHAR(32),
    test_session_id VARCHAR,
    test_definition_id VARCHAR,
    app_env_id VARCHAR,
    test_result VARCHAR,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    updated_at_day TIMESTAMP WITHOUT TIME ZONE,
    probes VARBIT
);
CREATE UNIQUE INDEX IF NOT EXISTS build_method_test_definition_coverage_table_pk ON metrics.build_method_test_definition_coverage_table (
    group_id,
    app_id,
    build_id,
    method_id,
    test_session_id,
    test_definition_id,
    COALESCE(app_env_id,''),
    COALESCE(test_result,'')
);
CREATE INDEX ON metrics.build_method_test_definition_coverage_table(group_id, updated_at_day);


CREATE TABLE IF NOT EXISTS metrics.build_method_test_session_coverage_table (
    group_id VARCHAR,
    app_id VARCHAR,
    build_id VARCHAR,
    method_id VARCHAR(32),
    test_session_id VARCHAR,
    app_env_id VARCHAR,
    test_result VARCHAR,
    test_tag VARCHAR,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    updated_at_day TIMESTAMP WITHOUT TIME ZONE,
    probes VARBIT
);
CREATE UNIQUE INDEX IF NOT EXISTS build_method_test_session_coverage_table_pk ON metrics.build_method_test_session_coverage_table (
    group_id,
    app_id,
    build_id,
    method_id,
    test_session_id,
    COALESCE(app_env_id,''),
    COALESCE(test_result,''),
    COALESCE(test_tag,'')
);
CREATE INDEX ON metrics.build_method_test_session_coverage_table(group_id, updated_at_day);



CREATE TABLE IF NOT EXISTS metrics.build_method_coverage_table (
    group_id VARCHAR,
    app_id VARCHAR,
    build_id VARCHAR,
    method_id VARCHAR(32),
    app_env_id VARCHAR,
    test_result VARCHAR,
    test_tag VARCHAR,
    test_task_id VARCHAR,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    updated_at_day TIMESTAMP WITHOUT TIME ZONE,
    probes VARBIT
);
CREATE UNIQUE INDEX IF NOT EXISTS build_method_coverage_table_pk ON metrics.build_method_coverage_table (
    group_id,
    app_id,
    build_id,
    method_id,
    COALESCE(app_env_id,''),
    COALESCE(test_result,''),
    COALESCE(test_tag,''),
    COALESCE(test_task_id,'')
);
CREATE INDEX ON metrics.build_method_coverage_table(group_id, updated_at_day);


CREATE TABLE IF NOT EXISTS metrics.method_coverage_table (
    group_id VARCHAR,
    app_id VARCHAR,
    method_id VARCHAR(32),
    branch VARCHAR,
    app_env_id VARCHAR,
    test_result VARCHAR,
    test_tag VARCHAR,
    test_task_id VARCHAR,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    updated_at_day TIMESTAMP WITHOUT TIME ZONE,
    probes VARBIT
);
CREATE UNIQUE INDEX IF NOT EXISTS method_coverage_table_pk ON metrics.method_coverage_table (
    group_id,
    app_id,
    method_id,
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_result,''),
    COALESCE(test_tag,''),
    COALESCE(test_task_id,'')
);
CREATE INDEX ON metrics.method_coverage_table(group_id, updated_at_day);


CREATE TABLE IF NOT EXISTS metrics.test_to_code_mapping_table (
    group_id VARCHAR,
    app_id VARCHAR,
    signature VARCHAR,
    test_definition_id VARCHAR,
    branch VARCHAR,
    app_env_id VARCHAR,
    test_task_id VARCHAR,
    created_at_day TIMESTAMP WITHOUT TIME ZONE,
    updated_at_day TIMESTAMP WITHOUT TIME ZONE
);
CREATE UNIQUE INDEX IF NOT EXISTS test_to_code_mapping_table_pk ON metrics.test_to_code_mapping_table (
    group_id,
    app_id,
    signature,
    test_definition_id,
    COALESCE(branch,''),
    COALESCE(app_env_id,''),
    COALESCE(test_task_id,'')
);
CREATE INDEX ON metrics.test_to_code_mapping_table(group_id, updated_at_day);