-----------------------------------------------------------------
-- Repeatable migration script to create views for metrics
-- Migration version: v3
-- Compatible with: R__1_Data.sql v4.3
-----------------------------------------------------------------

-----------------------------------------------------------------
-- Create a view of builds with information about
-- the number of methods and probes
-----------------------------------------------------------------
DROP VIEW IF EXISTS metrics.builds_with_statistics CASCADE;
CREATE OR REPLACE VIEW metrics.builds_with_statistics AS
SELECT
    group_id,
    app_id,
    build_id,
    version_id,
    build_version,
    commit_sha,
    branch,
    committed_at,
    commit_author,
    commit_message,
    created_at,
    app_env_ids,
    COALESCE(total_classes, 0) AS total_classes,
    COALESCE(total_methods, 0) AS total_methods,
    COALESCE(total_probes,  0) AS total_probes,
    validation_status,
    agent_version,
    agent_env,
    agent_params
FROM metrics.builds;

-----------------------------------------------------------------
-- Create a view of test definitions with information about
-- the number of test launches and average test duration
-----------------------------------------------------------------
DROP VIEW IF EXISTS metrics.test_definitions_with_statistics CASCADE;
CREATE OR REPLACE VIEW metrics.test_definitions_with_statistics AS
SELECT
    td.group_id,
    td.test_definition_id,
    MIN(td.test_name) AS test_name,
    MIN(td.test_path) AS test_path,
    MIN(td.test_runner) AS test_runner,
    MIN(td.test_tags) AS test_tags,
    COUNT(*) AS launches,
    AVG(COALESCE(tl.test_duration, 0)) AS test_duration_avg,
    SUM(COALESCE(tl.passed, 0)) AS passed,
    SUM(COALESCE(tl.failed, 0)) AS failed,
    SUM(COALESCE(tl.skipped, 0)) AS skipped,
    SUM(COALESCE(tl.smart_skipped, 0)) AS smart_skipped,
    SUM(COALESCE(tl.success, 0)) AS success,
    CASE
        WHEN COUNT(*) > 0 THEN CAST(SUM(tl.success) AS FLOAT) / COUNT(*)
        ELSE 1
    END AS success_rate
FROM metrics.test_definitions td
LEFT JOIN metrics.test_launches tl ON tl.group_id = td.group_id AND tl.test_definition_id = td.test_definition_id
GROUP BY td.group_id, td.test_definition_id;

-----------------------------------------------------------------
-- Create a view of test sessions with information about
-- the number of test definitions, launches, and overall results
-----------------------------------------------------------------
DROP VIEW IF EXISTS metrics.test_sessions_with_statistics CASCADE;
CREATE OR REPLACE VIEW metrics.test_sessions_with_statistics AS
SELECT
    ts.group_id,
    ts.test_session_id,
    MIN(ts.test_task_id) AS test_task_id,
    MIN(ts.test_project_id) AS test_project_id,
    MIN(ts.session_started_at) AS session_started_at,
    MIN(ts.created_by) AS created_by,
    COUNT(DISTINCT tl.test_definition_id) AS test_definitions,
    COUNT(*) AS test_launches,
    CASE
        WHEN SUM(tl.failed) > 0 THEN 'FAILED'
        WHEN SUM(tl.passed) > 0 THEN 'PASSED'
        WHEN SUM(tl.smart_skipped) > 0 THEN 'SMART_SKIPPED'
        WHEN SUM(tl.skipped) > 0 THEN 'SKIPPED'
        ELSE 'UNKNOWN'
    END AS result,
    SUM(COALESCE(tl.test_duration, 0)) AS test_duration,
    SUM(COALESCE(tl.failed, 0)) AS failed,
    SUM(COALESCE(tl.success, 0)) AS success,
    SUM(COALESCE(tl.passed, 0)) AS passed,
    SUM(COALESCE(tl.skipped, 0)) AS skipped,
    SUM(COALESCE(tl.smart_skipped, 0)) AS smart_skipped,
    CASE
        WHEN COUNT(*) > 0 THEN CAST(SUM(COALESCE(tl.success, 0)) AS FLOAT) / COUNT(*)
        ELSE 1
    END AS success_rate,
    SUM(CASE
        WHEN tl.smart_skipped > 0 THEN CAST(td.test_duration_avg AS bigint)
        ELSE 0
    END)::bigint AS time_saved
FROM metrics.test_sessions ts
LEFT JOIN metrics.test_launches tl ON tl.group_id = ts.group_id AND tl.test_session_id = ts.test_session_id
LEFT JOIN metrics.test_definitions_with_statistics td ON td.group_id = tl.group_id AND td.test_definition_id = tl.test_definition_id
GROUP BY ts.group_id, ts.test_session_id;

-----------------------------------------------------------------
-- Create a view of test file launches with information about
-- the number of test definitions, launches, and overall results
-----------------------------------------------------------------
DROP VIEW IF EXISTS metrics.test_file_launches_with_statistics CASCADE;
CREATE OR REPLACE VIEW metrics.test_file_launches_with_statistics AS
SELECT
    td.group_id,
    td.test_path,
    tl.test_session_id,
    COUNT(DISTINCT td.test_definition_id) AS test_definitions,
    COUNT(DISTINCT tl.test_launch_id) AS test_launches,
    CASE
        WHEN SUM(tl.failed) > 0 THEN 'FAILED'
        WHEN SUM(tl.passed) > 0 THEN 'PASSED'
        WHEN SUM(tl.smart_skipped) > 0 THEN 'SMART_SKIPPED'
        WHEN SUM(tl.skipped) > 0 THEN 'SKIPPED'
        ELSE 'UNKNOWN'
    END AS result,
    SUM(COALESCE(tl.failed, 0)) AS failed,
    SUM(COALESCE(tl.passed, 0)) AS passed,
    SUM(COALESCE(tl.skipped, 0)) AS skipped,
    SUM(COALESCE(tl.smart_skipped, 0)) AS smart_skipped,
    SUM(COALESCE(tl.success, 0)) AS success,
    SUM(COALESCE(tl.test_duration, 0)) AS test_duration,
    CASE
        WHEN COUNT(*) > 0 THEN CAST(SUM(COALESCE(tl.success, 0)) AS FLOAT) / COUNT(*)
        ELSE 1
    END AS success_rate
FROM metrics.test_definitions td
LEFT JOIN metrics.test_launches tl ON tl.group_id = td.group_id AND tl.test_definition_id = td.test_definition_id
GROUP BY td.group_id, td.test_path, tl.test_session_id;

-------------------------------------------------------------------
-- Create a view of test session definitions with information about
-- the number of test launches, total duration, and overall results
-------------------------------------------------------------------
DROP VIEW IF EXISTS metrics.test_session_definitions CASCADE;
CREATE OR REPLACE VIEW metrics.test_session_definitions AS
SELECT
    ts.test_session_id,
    td.test_definition_id,
    COUNT(*) AS test_launches,
    SUM(tl.test_duration) AS test_duration_sum,
    metrics.format_duration(SUM(tl.test_duration)::bigint) AS test_duration_sum_formatted,
    CASE
        WHEN SUM(tl.failed) > 0 THEN 'FAILED'
        WHEN SUM(tl.passed) > 0 THEN 'PASSED'
        WHEN SUM(tl.smart_skipped) > 0 THEN 'SMART_SKIPPED'
        WHEN SUM(tl.skipped) > 0 THEN 'SKIPPED'
        ELSE 'UNKNOWN'
    END AS test_result,
    MIN(td.test_name) AS test_name,
    MIN(td.test_path) AS test_path,
    MIN(td.test_runner) AS test_runner,
    MIN(td.test_tags) AS test_tags,
    array_to_string(MIN(td.test_tags), ', ') AS test_tags_formatted,
    MIN(td.test_project_id) AS test_project_id
FROM metrics.test_sessions ts
JOIN metrics.test_launches tl ON tl.test_session_id = ts.test_session_id AND tl.group_id = ts.group_id
JOIN metrics.test_definitions td ON td.group_id = tl.group_id AND td.test_definition_id = tl.test_definition_id
GROUP BY
    ts.test_session_id,
    td.test_definition_id;
