CREATE TABLE config.release_tracks
(
    group_id   VARCHAR(255)                        NOT NULL,
    track_id   VARCHAR(255)                        NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (group_id, track_id)
);

CREATE TABLE config.release_track_apps
(
    group_id VARCHAR(255) NOT NULL,
    track_id VARCHAR(255) NOT NULL,
    app_id   VARCHAR(255) NOT NULL,
    PRIMARY KEY (group_id, track_id, app_id),
    FOREIGN KEY (group_id, track_id) REFERENCES config.release_tracks (group_id, track_id) ON DELETE CASCADE
);

CREATE TABLE config.milestones
(
    group_id     VARCHAR(255)                        NOT NULL,
    track_id     VARCHAR(255)                        NOT NULL,
    milestone_id VARCHAR(255)                        NOT NULL,
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (group_id, track_id, milestone_id),
    FOREIGN KEY (group_id, track_id) REFERENCES config.release_tracks (group_id, track_id) ON DELETE CASCADE
);

CREATE TABLE config.milestone_apps
(
    group_id     VARCHAR(255) NOT NULL,
    track_id     VARCHAR(255) NOT NULL,
    milestone_id VARCHAR(255) NOT NULL,
    app_id       VARCHAR(255) NOT NULL,
    build_id     VARCHAR(255) NOT NULL,
    PRIMARY KEY (group_id, track_id, milestone_id, app_id),
    FOREIGN KEY (group_id, track_id, milestone_id) REFERENCES config.milestones (group_id, track_id, milestone_id) ON DELETE CASCADE,
    FOREIGN KEY (group_id, track_id, app_id) REFERENCES config.release_track_apps (group_id, track_id, app_id)
);

CREATE TABLE config.milestone_quality_gates
(
    group_id                                VARCHAR(255) NOT NULL,
    track_id                                VARCHAR(255) NOT NULL,
    milestone_id                            VARCHAR(255) NOT NULL,
    overall_coverage_target_percentage      NUMERIC(5, 2),
    overall_methods_coverage_target_percentage NUMERIC(5, 2),
    overall_passed_tests_target_percentage  NUMERIC(5, 2),
    impact_coverage_target_percentage       NUMERIC(5, 2),
    impact_methods_coverage_target_percentage NUMERIC(5, 2),
    impact_passed_tests_target_percentage   NUMERIC(5, 2),
    PRIMARY KEY (group_id, track_id, milestone_id),
    FOREIGN KEY (group_id, track_id, milestone_id) REFERENCES config.milestones (group_id, track_id, milestone_id) ON DELETE CASCADE
);

CREATE TABLE config.app_quality_gates
(
    group_id                            VARCHAR(255) NOT NULL,
    app_id                              VARCHAR(255) NOT NULL,
    code_coverage_target_percentage     NUMERIC(5, 2),
    method_coverage_target_percentage   NUMERIC(5, 2),
    tests_passed_target_percentage      NUMERIC(5, 2),
    change_coverage_target_percentage   NUMERIC(5, 2),
    change_methods_coverage_target_percentage NUMERIC(5, 2),
    impacted_tests_passed_target_percentage NUMERIC(5, 2),
    PRIMARY KEY (group_id, app_id)
);

CREATE TABLE config.test_projects
(
    group_id        VARCHAR(255)                NOT NULL,
    test_project_id VARCHAR(255)                NOT NULL,
    test_type       VARCHAR(50) NOT NULL DEFAULT '_Other_',
    PRIMARY KEY (group_id, test_project_id)
);
