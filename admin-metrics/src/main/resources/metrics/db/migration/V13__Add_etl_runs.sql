CREATE TABLE IF NOT EXISTS metrics.etl_runs (
    orchestrator_name  VARCHAR(225) NOT NULL,
    group_id           VARCHAR(225) NOT NULL DEFAULT '',
    app_id             VARCHAR(225) NOT NULL DEFAULT '',
    instance_id        VARCHAR(225) NOT NULL DEFAULT '',
    build_id           VARCHAR(225) NOT NULL DEFAULT '',
    test_session_id    VARCHAR(225) NOT NULL DEFAULT '',
    test_definition_id VARCHAR(225) NOT NULL DEFAULT '',
    test_launch_id     VARCHAR(225) NOT NULL DEFAULT '',
    runs_count         BIGINT       NOT NULL DEFAULT 0,
    status             VARCHAR(50)  NOT NULL,
    last_started_at    TIMESTAMP    NULL,
    last_finished_at   TIMESTAMP    NULL,
    last_processed_at  TIMESTAMP    NULL,
    lock_owner         VARCHAR(255) NULL,
    lock_expires_at    TIMESTAMP    NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (
        orchestrator_name,
        group_id,
        app_id,
        instance_id,
        build_id,
        test_session_id,
        test_definition_id,
        test_launch_id
    )
);
