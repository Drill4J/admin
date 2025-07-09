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
	FROM raw_data.builds b;
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
	    md5(signature||':'||body_checksum||':'||probes_count) as method_id,
		group_id,
	    app_id,
	    signature,
	    MIN(name) AS method_name,
	    MIN(classname) AS class_name,
	    MIN(params) AS method_params,
	    MIN(return_type) AS return_type,
	    body_checksum,
	    probes_count
	FROM raw_data.view_methods_with_rules
	GROUP BY group_id, app_id, signature, body_checksum, probes_count;
CREATE UNIQUE INDEX IF NOT EXISTS idx_methods_pk ON metrics.methods (group_id, app_id, method_id);

-----------------------------------------------------------------
-- Create a materialized view of specific build methods
-- by copying data from the `raw_data.view_methods_with_rules` view
-- grouping by build ID, method signature, body checksum, and probes count
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.build_methods CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.build_methods AS
    SELECT
	    group_id,
		app_id,
	    build_id,
	    md5(signature||':'||body_checksum||':'||probes_count) as method_id,
		MIN(probes_start) AS probes_start,
		MIN(method_num) AS method_num
	FROM raw_data.view_methods_with_rules m
	GROUP BY group_id, app_id, build_id, signature, body_checksum, probes_count;
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
FROM raw_data.test_launches tl;
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
FROM raw_data.test_definitions td;
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
    DATE_TRUNC('day', ts.created_at) AS creation_day
FROM raw_data.test_sessions ts;
CREATE UNIQUE INDEX IF NOT EXISTS idx_test_sessions_pk ON metrics.test_sessions (group_id, test_session_id);

-----------------------------------------------------------------
-- Create a materialized view of method coverage
-- by copying data from the `raw_data.view_methods_coverage_v2` view
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.method_coverage CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.method_coverage AS
    SELECT
	    c.group_id,
	    c.app_id,
		MD5(c.signature||':'||c.body_checksum||':'||c.probes_count) AS method_id,
		c.build_id,
		c.env_id AS app_env_id,
		c.test_launch_id,
		MIN(b.branch) AS branch,
        MIN(td.test_tags) AS test_tags,
        MIN(td.test_path) AS test_path,
        MIN(td.test_name) AS test_name,
        MIN(ts.test_task_id) AS test_task_id,
        MIN(tl.test_result) AS test_result,
        MIN(tl.test_session_id) AS test_session_id,
        MIN(tl.test_definition_id) AS test_definition_id,
        BIT_OR(c.probes) AS probes,
	    DATE_TRUNC('day', c.created_at) AS creation_day
	FROM raw_data.view_methods_coverage_v2 c
	JOIN metrics.builds b ON b.group_id = c.group_id AND b.app_id = c.app_id AND b.build_id = c.build_id
    LEFT JOIN metrics.test_launches tl ON tl.group_id = c.group_id AND tl.test_launch_id = c.test_launch_id
    LEFT JOIN metrics.test_definitions td ON td.group_id = tl.group_id AND td.test_definition_id = tl.test_definition_id
    LEFT JOIN metrics.test_sessions ts ON ts.group_id = tl.group_id AND ts.test_session_id = tl.test_session_id
    GROUP BY c.group_id, c.app_id, c.signature, c.body_checksum, c.probes_count, c.build_id, c.env_id, c.test_launch_id, DATE_TRUNC('day', c.created_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_method_coverage_pk ON metrics.method_coverage (group_id, app_id, method_id, build_id, app_env_id, test_launch_id, creation_day);
CREATE INDEX ON metrics.method_coverage(group_id, app_id, build_id);
CREATE INDEX ON metrics.method_coverage(group_id, app_id, method_id);
CREATE INDEX ON metrics.method_coverage(group_id, app_id, method_id, test_launch_id);
CREATE INDEX ON metrics.method_coverage(group_id, app_id, method_id, test_session_id);

-----------------------------------------------------------------
-- Create a materialized view of method smart coverage
-- by aggregating data from the `metrics.method_coverage` view
-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS metrics.method_smartcoverage CASCADE;
CREATE MATERIALIZED VIEW IF NOT EXISTS metrics.method_smartcoverage AS
    SELECT
	    c.group_id,
	    c.app_id,
		c.method_id,
		c.app_env_id,
		c.branch,
		c.test_tags,
		c.test_task_id,
		c.test_result,
		c.creation_day,
		BIT_OR(c.probes) AS probes
	FROM metrics.method_coverage c
	GROUP BY c.group_id, c.app_id, c.method_id, c.branch, c.app_env_id, c.test_tags, c.test_task_id, c.test_result, c.creation_day;
CREATE UNIQUE INDEX ON metrics.method_smartcoverage(group_id, app_id, method_id, branch, app_env_id, test_tags, test_task_id, test_result, creation_day);
CREATE INDEX ON metrics.method_smartcoverage(group_id, app_id, method_id);

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
    JOIN raw_data.builds b ON b.group_id = tsb.group_id AND b.id = tsb.build_id
    UNION ALL
    SELECT DISTINCT
      c.group_id,
      c.app_id,
      c.build_id,
      c.test_session_id
    FROM metrics.method_coverage c
    WHERE c.test_session_id IS NOT NULL
) tsb;
CREATE UNIQUE INDEX IF NOT EXISTS idx_test_session_builds_pk ON metrics.test_session_builds (group_id, app_id, build_id, test_session_id);
