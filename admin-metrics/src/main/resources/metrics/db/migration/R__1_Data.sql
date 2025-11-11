-----------------------------------------------------------------
-- Repeatable migration script to create materialized views for metrics
-- Migration version: v4
-----------------------------------------------------------------

-----------------------------------------------------------------
-- Create a materialized view of last updated timestamps
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.last_update_status CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.last_update_status AS
SELECT
    b.group_id,
	metrics.get_metrics_period(b.group_id) AS since_timestamp,
	CURRENT_TIMESTAMP AS until_timestamp
FROM raw_data.builds b
GROUP BY b.group_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_last_update_status_pk ON metrics.last_update_status (group_id);

-----------------------------------------------------------------
-- Create a materialized view of builds
-- by copying data from the `raw_data.builds` table
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.builds CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.builds AS
SELECT
    b.group_id,
    b.app_id,
    b.id AS build_id,
    split_part(b.id, ':', 3) AS version_id,
    (SELECT ARRAY_AGG(DISTINCT env_id) FROM raw_data.instances WHERE env_id != '' AND build_id = b.id) AS app_env_ids,
    b.build_version,
    b.branch,
    b.commit_sha,
    b.commit_author,
    b.commit_message,
    b.committed_at,
    b.created_at,
    DATE_TRUNC('day', b.created_at) AS creation_day
FROM raw_data.builds b
JOIN metrics.last_update_status lus ON lus.group_id = b.group_id
WHERE b.created_at >= lus.since_timestamp AND b.created_at < lus.until_timestamp
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_builds_pk ON metrics.builds (group_id, app_id, build_id);
CREATE INDEX ON metrics.builds(group_id, app_id, build_id, created_at);

-----------------------------------------------------------------
-- Create a materialized view of methods
-- by copying data from the `raw_data.view_methods_with_rules` view
-- grouping by method signature, body checksum, and probes count
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.methods CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.methods AS
SELECT
    md5(m.signature||':'||m.body_checksum||':'||m.probes_count) as method_id,
    m.group_id,
    m.app_id,
    m.signature,
    MIN(m.name) AS method_name,
    MIN(m.classname) AS class_name,
    MIN(m.params) AS method_params,
    MIN(m.return_type) AS return_type,
    m.body_checksum,
    m.probes_count
FROM raw_data.view_methods_with_rules m
JOIN metrics.builds b ON b.group_id = m.group_id AND b.app_id = m.app_id AND b.build_id = m.build_id
GROUP BY m.group_id, m.app_id, m.signature, m.body_checksum, m.probes_count
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_methods_pk ON metrics.methods (group_id, app_id, method_id);
CREATE INDEX ON metrics.methods(group_id, app_id, signature);
CREATE INDEX ON metrics.methods(group_id, app_id, class_name);

-----------------------------------------------------------------
-- Create a materialized view of specific build methods
-- by copying data from the `raw_data.view_methods_with_rules` view
-- grouping by build ID, method signature, body checksum, and probes count
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.build_methods CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.build_methods AS
SELECT
    m.group_id,
    m.app_id,
    m.build_id,
    md5(m.signature||':'||m.body_checksum||':'||m.probes_count) as method_id,
    MIN(m.probe_start_pos) AS class_probes_start
FROM raw_data.view_methods_with_rules m
JOIN metrics.builds b ON b.group_id = m.group_id AND b.app_id = m.app_id AND b.build_id = m.build_id
GROUP BY m.group_id, m.app_id, m.build_id, m.signature, m.body_checksum, m.probes_count
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_build_methods_pk ON metrics.build_methods (group_id, app_id, build_id, method_id);

-----------------------------------------------------------------
-- Create a materialized view of test launches
-- by copying data from the `raw_data.test_launches` table
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.test_launches CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.test_launches AS
SELECT
    tl.id AS test_launch_id,
    tl.group_id,
    tl.test_definition_id,
    tl.test_session_id,
    tl.result AS test_result,
    tl.duration AS test_duration,
    tl.created_at,
    DATE_TRUNC('day', tl.created_at) AS creation_day,
    CASE WHEN tl.result = 'PASSED' THEN 1 ELSE 0 END AS passed,
    CASE WHEN tl.result = 'FAILED' THEN 1 ELSE 0 END AS failed,
    CASE WHEN tl.result = 'SKIPPED' THEN 1 ELSE 0 END AS skipped,
    CASE WHEN tl.result = 'SMART_SKIPPED' THEN 1 ELSE 0 END AS smart_skipped,
    CASE WHEN tl.result <> 'FAILED' THEN 1 ELSE 0 END AS success
FROM raw_data.test_launches tl
JOIN metrics.last_update_status lus ON lus.group_id = tl.group_id
WHERE tl.created_at >= lus.since_timestamp AND tl.created_at < lus.until_timestamp
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_test_launches_pk ON metrics.test_launches (group_id, test_launch_id);

-----------------------------------------------------------------
-- Create a materialized view of test definitions
-- by copying data from the `raw_data.test_definitions` table
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.test_definitions CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.test_definitions AS
SELECT
    td.id AS test_definition_id,
    td.group_id,
    td.path AS test_path,
    td.name AS test_name,
    td.runner AS test_runner,
    td.tags AS test_tags,
    td.metadata AS test_metadata,
    td.created_at,
    DATE_TRUNC('day', td.created_at) AS creation_day
FROM raw_data.test_definitions td
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_test_definitions_pk ON metrics.test_definitions (group_id, test_definition_id);

-----------------------------------------------------------------
-- Create a materialized view of test sessions
-- by copying data from the `raw_data.test_sessions` table
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.test_sessions CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.test_sessions AS
SELECT
    ts.id AS test_session_id,
    ts.group_id,
    ts.test_task_id,
    ts.started_at AS session_started_at,
    ts.created_at,
    DATE_TRUNC('day', ts.created_at) AS creation_day,
    ts.created_by
FROM raw_data.test_sessions ts
JOIN metrics.last_update_status lus ON lus.group_id = ts.group_id
WHERE ts.created_at >= lus.since_timestamp AND ts.created_at < lus.until_timestamp
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_test_sessions_pk ON metrics.test_sessions (group_id, test_session_id);

------------------------------------------------------------------
-- Create a materialized view of class coverage per test definition
-- by aggregation data from the `raw_data.coverage` table
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.build_class_test_definition_coverage CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.build_class_test_definition_coverage AS
SELECT
    c.group_id,
    c.app_id,
    i.build_id,
    c.classname AS class_name,
    c.test_session_id,
    tl.test_definition_id,
    i.env_id AS app_env_id,
    tl.test_result,
    DATE_TRUNC('day', c.created_at) AS creation_day,
    BIT_OR(c.probes) AS probes
FROM raw_data.coverage c
JOIN raw_data.instances i ON i.group_id = c.group_id AND i.app_id = c.app_id AND i.id = c.instance_id
LEFT JOIN metrics.test_launches tl ON tl.group_id = c.group_id AND tl.test_launch_id = c.test_id
JOIN metrics.last_update_status lus ON lus.group_id = c.group_id
WHERE c.created_at >= lus.since_timestamp AND c.created_at < lus.until_timestamp
    AND NOT EXISTS (
        SELECT 1
        FROM raw_data.method_ignore_rules r
        WHERE r.group_id = c.group_id
            AND r.app_id = c.app_id
            AND r.classname_pattern IS NOT NULL AND c.classname::text ~ r.classname_pattern::text)
GROUP BY c.group_id, c.app_id, i.build_id, c.classname, c.test_session_id, tl.test_definition_id, i.env_id, tl.test_result, DATE_TRUNC('day', c.created_at)
WITH NO DATA;
CREATE UNIQUE INDEX IF NOT EXISTS idx_build_class_test_definition_coverage_pk ON metrics.build_class_test_definition_coverage (group_id, app_id, build_id, class_name, test_session_id, test_definition_id, app_env_id, test_result, creation_day);
-- Used in build_method_test_definition_coverage
CREATE INDEX ON metrics.build_class_test_definition_coverage(group_id, app_id, class_name, build_id);

------------------------------------------------------------------
-- Create a materialized view of method coverage per test definition
-- by aggregation data from the `metrics.build_method_test_launch_coverage_view`
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.build_method_test_definition_coverage CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.build_method_test_definition_coverage AS
SELECT
    c.group_id,
    c.app_id,
    c.build_id,
    m.method_id,
    c.test_session_id,
    c.test_definition_id,
    c.app_env_id,
    c.test_result,
    c.creation_day,
    BIT_OR(SUBSTRING(c.probes FROM bm.class_probes_start + 1 FOR m.probes_count)) AS probes
FROM metrics.build_class_test_definition_coverage c
JOIN metrics.methods m ON m.group_id = c.group_id AND m.app_id = c.app_id AND m.class_name = c.class_name
JOIN metrics.build_methods bm ON bm.group_id = m.group_id AND bm.app_id = m.app_id AND bm.build_id = c.build_id AND bm.method_id = m.method_id
    AND BIT_COUNT(SUBSTRING(c.probes FROM bm.class_probes_start + 1 FOR m.probes_count)) > 0
    AND BIT_LENGTH(SUBSTRING(c.probes FROM bm.class_probes_start + 1 FOR m.probes_count)) = m.probes_count
GROUP BY c.group_id, c.app_id, c.build_id, m.method_id, c.test_session_id, c.test_definition_id, c.app_env_id, c.test_result, c.creation_day
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_build_method_test_definition_coverage_pk ON metrics.build_method_test_definition_coverage (group_id, app_id, build_id, method_id, test_session_id, test_definition_id, app_env_id, test_result, creation_day);
-- Used in build_method_test_session_coverage
CREATE INDEX ON metrics.build_method_test_definition_coverage(group_id, test_definition_id);
-- Used in test_to_code_mapping
CREATE INDEX ON metrics.build_method_test_definition_coverage(group_id, app_id, build_id, method_id, test_session_id) WHERE test_result = 'PASSED';

------------------------------------------------------------------
-- Create a materialized view of method coverage per test session
-- by copying data from the `metrics.build_method_test_definition_coverage` view
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.build_method_test_session_coverage CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.build_method_test_session_coverage AS
SELECT
    c.group_id,
    c.app_id,
    c.build_id,
    c.method_id,
    c.test_session_id,
    c.app_env_id,
    test_tag,
    c.test_result,
    c.creation_day,
    BIT_OR(c.probes) AS probes
FROM metrics.build_method_test_definition_coverage c
LEFT JOIN metrics.test_definitions td ON td.group_id = c.group_id AND td.test_definition_id = c.test_definition_id
LEFT JOIN LATERAL unnest(td.test_tags) AS test_tag ON TRUE
GROUP BY c.group_id, c.app_id, c.build_id, c.method_id, c.test_session_id, c.app_env_id, test_tag, c.test_result, c.creation_day
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_build_method_test_session_coverage_pk ON metrics.build_method_test_session_coverage (group_id, app_id, build_id, method_id, test_session_id, app_env_id, test_tag, test_result, creation_day);
-- Used in build_method_coverage
CREATE INDEX ON metrics.build_method_test_session_coverage(group_id, test_session_id);
-- Used in test_session_builds
CREATE INDEX ON metrics.build_method_test_session_coverage(test_session_id) WHERE test_session_id IS NOT NULL;
-- Used in get_methods_with_coverage_by_test_session
CREATE INDEX ON metrics.build_method_test_session_coverage(group_id, app_id, build_id, method_id, test_session_id) WHERE test_session_id IS NOT NULL;

-----------------------------------------------------------------
-- Create a materialized view of method coverage
-- by copying data from the `raw_data.view_methods_coverage_v2` view
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.build_method_coverage CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.build_method_coverage AS
SELECT
    c.group_id,
    c.app_id,
    c.build_id,
    c.method_id,
    c.app_env_id,
    ts.test_task_id,
    c.test_tag,
    c.test_result,
    c.creation_day,
    BIT_OR(c.probes) AS probes
FROM metrics.build_method_test_session_coverage c
LEFT JOIN metrics.test_sessions ts ON ts.group_id = c.group_id AND ts.test_session_id  = c.test_session_id
GROUP BY c.group_id, c.app_id, c.build_id, c.method_id, c.app_env_id, ts.test_task_id, c.test_tag, c.test_result, c.creation_day
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_build_method_coverage_pk ON metrics.build_method_coverage (group_id, app_id, build_id, method_id, app_env_id, test_task_id, test_tag, test_result, creation_day);
-- Used in method_coverage
CREATE INDEX ON metrics.build_method_coverage(group_id, app_id, method_id);
-- Used in Build Summary Dashboard
CREATE INDEX ON metrics.build_method_coverage(group_id, app_id, build_id, method_id, app_env_id, test_tag);
CREATE INDEX ON metrics.build_method_coverage(group_id, app_id, build_id, method_id, test_tag);

-----------------------------------------------------------------
-- Create a materialized view of method coverage
-- by aggregating data from the `metrics.build_method_coverage` view
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.method_coverage CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.method_coverage AS
SELECT
    c.group_id,
    c.app_id,
    c.method_id,
    b.branch,
    c.app_env_id,
    c.test_task_id,
    c.test_tag,
    c.test_result,
    c.creation_day,
    BIT_OR(c.probes) AS probes
FROM metrics.build_method_coverage c
JOIN metrics.builds b ON b.group_id = c.group_id AND b.app_id = c.app_id AND b.build_id = c.build_id
GROUP BY c.group_id, c.app_id, c.method_id, b.branch, c.app_env_id, c.test_task_id, c.test_tag, c.test_result, c.creation_day
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_method_coverage_pk ON metrics.method_coverage (group_id, app_id, method_id, branch, app_env_id, test_task_id, test_tag, test_result, creation_day);
-- Used in get_methods_with_coverage
CREATE INDEX ON metrics.method_coverage(group_id, app_id, method_id, creation_day);
-- Used in Build Summary Dashboard
CREATE INDEX ON metrics.method_coverage(group_id, app_id, method_id, app_env_id, test_tag);
CREATE INDEX ON metrics.method_coverage(group_id, app_id, method_id, test_tag);

-----------------------------------------------------------------
-- Create a materialized view of test session builds
-- by aggregating data from the `raw_data.test_session_builds` and `metrics.method_coverage` tables
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.test_session_builds CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.test_session_builds AS
SELECT DISTINCT
  tsb.group_id,
  tsb.app_id,
  tsb.build_id,
  tsb.test_session_id
FROM (
	SELECT
        tsb.group_id,
        b.app_id,
        tsb.build_id,
        tsb.test_session_id
    FROM raw_data.test_session_builds tsb
    JOIN metrics.builds b ON b.group_id = tsb.group_id AND b.build_id = tsb.build_id
    UNION ALL
    SELECT DISTINCT
      c.group_id,
      c.app_id,
      c.build_id,
      c.test_session_id
    FROM metrics.build_method_test_session_coverage c
    WHERE c.test_session_id IS NOT NULL
) tsb
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_test_session_builds_pk ON metrics.test_session_builds (group_id, app_id, build_id, test_session_id);

-----------------------------------------------------------------
-- Create a materialized view of test to method mapping
-- by aggregating data from the `metrics.build_method_test_definition_coverage` view
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.test_to_code_mapping CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.test_to_code_mapping AS
SELECT
    c.group_id,
    c.app_id,
    m.signature,
    c.test_definition_id,
    b.branch,
    c.app_env_id,
    MIN(m.class_name) AS class_name,
    MIN(m.method_name) AS method_name,
    MIN(m.method_params) AS method_params,
    MIN(m.return_type) AS return_type,
    ts.test_task_id
FROM metrics.build_method_test_definition_coverage c
JOIN metrics.builds b ON b.group_id = c.group_id AND b.app_id = c.app_id AND b.build_id = c.build_id
JOIN metrics.methods m ON m.group_id = c.group_id AND m.app_id = c.app_id AND m.method_id = c.method_id
JOIN metrics.test_sessions ts ON ts.group_id = c.group_id AND ts.test_session_id = c.test_session_id
WHERE c.test_result = 'PASSED'
GROUP BY c.group_id, c.app_id, m.signature, c.test_definition_id, b.branch, c.app_env_id, ts.test_task_id
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_test_to_code_mapping_pk ON metrics.test_to_code_mapping (group_id, app_id, signature, test_definition_id, branch, app_env_id, test_task_id);
-- Used in get_impacted_tests, get_impacted_methods
CREATE INDEX ON metrics.test_to_code_mapping(group_id, app_id, signature, test_definition_id, app_env_id, test_task_id);
CREATE INDEX ON metrics.test_to_code_mapping(group_id, app_id, signature, test_task_id);