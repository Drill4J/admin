-----------------------------------------------------------------
--Deprecated, use view_methods_coverage_v2
-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_methods_coverage AS
    SELECT
        builds.group_id,
        builds.app_id,
        methods.signature,
        methods.body_checksum,
        BIT_LENGTH(coverage.probes) AS probes_count,
        methods.build_id,
        SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count) AS probes,
        BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) AS covered_probes,
        coverage.created_at,
        builds.branch,
        instances.env_id,
        launches.result as test_result,
        sessions.test_task_id,
        launches.test_definition_id,
        launches.id as test_launch_id
    FROM raw_data.coverage coverage
    JOIN raw_data.instances instances ON instances.id = coverage.instance_id
    JOIN raw_data.methods methods ON methods.classname = coverage.classname AND methods.build_id = instances.build_id
    JOIN raw_data.builds builds ON builds.id = methods.build_id
    LEFT JOIN raw_data.test_launches launches ON launches.id = coverage.test_id
    LEFT JOIN raw_data.test_sessions sessions ON sessions.id = launches.test_session_id
    WHERE BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) > 0;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_methods_with_rules AS
    SELECT signature,
        name,
        classname,
        params,
        return_type,
        body_checksum,
        probes_count,
        build_id,
        group_id,
        app_id,
        probe_start_pos
    FROM raw_data.methods m
    WHERE probes_count > 0
        AND NOT EXISTS (
            SELECT 1
            FROM raw_data.method_ignore_rules r
            WHERE r.group_id = m.group_id
		        AND r.app_id = m.app_id
		        AND (r.name_pattern IS NOT NULL AND m.name::text ~ r.name_pattern::text
		            OR r.classname_pattern IS NOT NULL AND m.classname::text ~ r.classname_pattern::text
		            OR r.annotations_pattern IS NOT NULL AND m.annotations::text ~ r.annotations_pattern::text
		            OR r.class_annotations_pattern IS NOT NULL AND m.class_annotations::text ~ r.class_annotations_pattern::text));

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_methods_coverage_v2 AS
    SELECT
        builds.group_id,
        builds.app_id,
		methods.build_id,
		instances.env_id,
		builds.branch,
		definitions.tags AS test_tags,
        methods.signature,
        methods.body_checksum,
        BIT_LENGTH(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) AS probes_count,
        SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count) AS probes,
        BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) AS covered_probes,
        coverage.created_at,
        builds.created_at AS build_created_at,
        launches.result AS test_result,
        launches.id AS test_launch_id,
        sessions.id AS test_session_id,
        sessions.test_task_id AS test_task_id,
        definitions.id AS test_definition_id,
        definitions.path AS test_path
    FROM raw_data.coverage coverage
	JOIN raw_data.view_methods_with_rules methods ON methods.classname = coverage.classname
    JOIN raw_data.instances instances ON instances.id = coverage.instance_id
    JOIN raw_data.builds builds ON builds.id = methods.build_id AND builds.id = instances.build_id
    LEFT JOIN raw_data.test_launches launches ON launches.id = coverage.test_id
	LEFT JOIN raw_data.test_definitions definitions ON definitions.id = launches.test_definition_id
	LEFT JOIN raw_data.test_sessions sessions ON sessions.id = launches.test_session_id
    WHERE TRUE
	  AND methods.probes_count > 0
	  AND BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) > 0
      AND BIT_LENGTH(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) = methods.probes_count;

-----------------------------------------------------------------
-- Deprecated, use raw_data.view_test_sessions_v2
-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_test_sessions AS
    SELECT
        ts.id AS test_session_id,
        ts.group_id,
        ts.test_task_id,
        ts.started_at,
        COUNT(*) AS tests,
        (CASE WHEN SUM(CASE WHEN tl.result = 'FAILED' THEN 1 ELSE 0 END) > 0 THEN 'FAILED' ELSE 'PASSED' END) AS result,
        (
            SELECT
              i.build_id
            FROM
              raw_data.coverage c
            JOIN raw_data.test_launches tl ON tl.id = c.test_id
            JOIN raw_data.instances i ON i.id = c.instance_id
            WHERE tl.test_session_id = ts.id
            LIMIT 1
          ) AS build_id,
        sum(tl.duration) AS duration,
        SUM(CASE WHEN tl.result = 'FAILED' THEN 1 ELSE 0 END) AS failed,
        SUM(CASE WHEN tl.result = 'PASSED' THEN 1 ELSE 0 END) AS passed,
        SUM(CASE WHEN tl.result = 'SKIPPED' THEN 1 ELSE 0 END) AS skipped,
        SUM(CASE WHEN tl.result = 'SMART_SKIPPED' THEN 1 ELSE 0 END) AS smart_skipped,
        SUM(CASE WHEN tl.result <> 'FAILED' THEN 1 ELSE 0 END) AS success,
        (CASE WHEN COUNT(*) > 0 THEN
			CAST(SUM(CASE WHEN tl.result <> 'FAILED' THEN 1 ELSE 0 END) AS FLOAT) / COUNT(*)
		 ELSE 1 END) AS successful
    FROM raw_data.test_sessions ts
    LEFT JOIN raw_data.test_launches tl ON ts.id = tl.test_session_id
    GROUP BY ts.id;

-----------------------------------------------------------------
-- Deprecated, use raw_data.view_methods_coverage_v2
-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_methods_tests_coverage AS
    SELECT
        builds.group_id,
        builds.app_id,
        methods.build_id,
        instances.env_id,
        builds.branch,
        launches.id AS test_launch_id,
        sessions.id AS test_session_id,
        sessions.test_task_id AS test_task_id,
        launches.test_definition_id,
        definitions.tags AS test_tags,
        methods.signature,
        methods.body_checksum,
        BIT_LENGTH(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) AS probes_count,
        SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count) AS probes,
        BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) AS covered_probes,
        coverage.created_at,
        launches.result AS test_result
    FROM raw_data.coverage coverage
    JOIN raw_data.methods methods ON methods.classname = coverage.classname
    JOIN raw_data.instances instances ON instances.id = coverage.instance_id
    JOIN raw_data.builds builds ON builds.id = methods.build_id AND builds.id = instances.build_id
    JOIN raw_data.test_launches launches ON launches.id = coverage.test_id
    JOIN raw_data.test_definitions definitions ON definitions.id = launches.test_definition_id
    JOIN raw_data.test_sessions sessions ON sessions.id = launches.test_session_id
    WHERE TRUE
      AND methods.probes_count > 0
      AND BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) > 0
      AND BIT_LENGTH(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) = methods.probes_count;

-----------------------------------------------------------------
--Deprecated, use raw_data.view_test_session_builds_v2
-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_test_session_builds AS
SELECT
  test_session_id,
  build_id,
  SUM(build_tests) AS build_tests
FROM (
	SELECT
	  test_session_id,
	  build_id,
	  0 AS build_tests
	FROM raw_data.test_session_builds
	UNION
	SELECT
		tl.test_session_id,
	  	tl.build_id,
	  	COUNT(*) AS build_tests
 	FROM (
		SELECT
		  tl.id,
		  tl.test_session_id,
		  i.build_id
		FROM
		  raw_data.coverage c
		JOIN raw_data.test_launches tl ON tl.id = c.test_id
		JOIN raw_data.instances i ON i.id = c.instance_id
		GROUP BY tl.id, tl.test_session_id, i.build_id
	) tl
	GROUP BY tl.test_session_id, tl.build_id
) tsb
GROUP BY test_session_id, build_id;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_test_session_builds_v2 AS
SELECT
  tlb.test_session_id,
  tlb.build_id,
  COUNT(*) AS build_tests
FROM (
	SELECT DISTINCT
	 	test_launch_id,
		test_session_id,
		build_id
	FROM (
		SELECT
			tl.id AS test_launch_id,
			tl.test_session_id,
			tsb.build_id
		FROM raw_data.test_launches tl
		JOIN raw_data.test_session_builds tsb ON tsb.test_session_id = tl.test_session_id
		GROUP BY tl.id, tl.test_session_id, tsb.build_id
		UNION ALL
		SELECT
		  tl.id AS test_launch_id,
		  tl.test_session_id,
		  i.build_id
		FROM raw_data.coverage c
		JOIN raw_data.test_launches tl ON tl.id = c.test_id
		JOIN raw_data.instances i ON i.id = c.instance_id
		GROUP BY tl.id, tl.test_session_id, i.build_id
	) tlb
) tlb
GROUP BY tlb.test_session_id, tlb.build_id;


-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_test_launches AS
SELECT
    tl.id AS test_launch_id,
    tl.test_session_id,
    tl.duration,
    tl.result,
    td.id AS test_definition_id,
    td.name AS test_name,
    td.path,
    td.runner,
    array_to_string(td.tags, ',') AS tags,
    (CASE WHEN tl.result = 'PASSED' THEN 1 ELSE 0 END) AS passed,
    (CASE WHEN tl.result = 'FAILED' THEN 1 ELSE 0 END) AS failed,
    (CASE WHEN tl.result = 'SKIPPED' THEN 1 ELSE 0 END) AS skipped,
    (CASE WHEN tl.result = 'SMART_SKIPPED' THEN 1 ELSE 0 END) AS smart_skipped,
    (CASE WHEN tl.result <> 'FAILED' THEN 1 ELSE 0 END) AS success
FROM raw_data.test_launches tl
LEFT JOIN raw_data.test_definitions td ON td.id = tl.test_definition_id;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_test_definitions AS
SELECT
  td.id AS test_definition_id,
  td.group_id,
  td.name AS test_name,
  td.path,
  td.runner,
  array_to_string(td.tags, ',') AS tags,
  COUNT(*) AS launches,
  AVG(tl.duration) AS avg_duration,
  SUM(tl.passed) AS passed,
  SUM(tl.failed) AS failed,
  SUM(tl.skipped) AS skipped,
  SUM(tl.smart_skipped) AS smart_skipped,
  SUM(tl.success) AS success,
  CAST(SUM(CASE WHEN tl.result <> 'FAILED' THEN 1 ELSE 0 END) AS FLOAT) / COUNT(*) AS successful
FROM raw_data.test_definitions td
LEFT JOIN raw_data.view_test_launches tl ON tl.test_definition_id = td.id
GROUP BY td.id;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_test_tasks AS
SELECT
    ts.group_id,
    ts.test_task_id,
    tsb.build_id,
    COUNT(*) AS sessions,
    SUM(ts.tests) AS tests,
    SUM(ts.duration) AS duration,
    MIN(ts.started_at) AS started_at,
    (CASE
        WHEN SUM(CASE WHEN ts.result = 'FAILED' THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
        WHEN SUM(CASE WHEN ts.result = 'PASSED' THEN 1 ELSE 0 END) > 0 THEN 'PASSED'
        WHEN SUM(CASE WHEN ts.result = 'SMART_SKIPPED' THEN 1 ELSE 0 END) > 0 THEN 'SMART_SKIPPED'
        WHEN SUM(CASE WHEN ts.result = 'SKIPPED' THEN 1 ELSE 0 END) > 0 THEN 'SKIPPED'
    ELSE 'UNKNOWN' END) AS result,
    SUM(ts.passed) AS passed,
    SUM(ts.failed) AS failed,
    SUM(ts.skipped) AS skipped,
    SUM(ts.smart_skipped) AS smart_skipped,
    SUM(ts.success) AS success,
    (CASE WHEN COUNT(*) > 0 THEN
        CAST(SUM(CASE WHEN ts.result <> 'FAILED' THEN 1 ELSE 0 END) AS FLOAT) / COUNT(*)
    ELSE 1 END) AS successful
FROM raw_data.view_test_sessions ts
JOIN raw_data.view_test_session_builds_v2 tsb ON tsb.test_session_id = ts.test_session_id
GROUP BY ts.group_id, ts.test_task_id, tsb.build_id;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_test_paths AS
SELECT
  tl.test_session_id,
  td.path,
  COUNT(*) AS tests,
  SUM(tl.duration) AS duration,
  (
    CASE WHEN SUM(CASE WHEN tl.result = 'FAILED' THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
         WHEN SUM(CASE WHEN tl.result = 'PASSED' THEN 1 ELSE 0 END) > 0 THEN 'PASSED'
         WHEN SUM(CASE WHEN tl.result = 'SMART_SKIPPED' THEN 1 ELSE 0 END) > 0 THEN 'SMART_SKIPPED'
         WHEN SUM(CASE WHEN tl.result = 'SKIPPED' THEN 1 ELSE 0 END) > 0 THEN 'SKIPPED'
    ELSE 'UNKNOWN' END
  ) AS result,
  SUM(CASE WHEN tl.result = 'PASSED' THEN 1 ELSE 0 END) AS passed,
  SUM(CASE WHEN tl.result = 'FAILED' THEN 1 ELSE 0 END) AS failed,
  SUM(CASE WHEN tl.result = 'SKIPPED' THEN 1 ELSE 0 END) AS skipped,
  SUM(CASE WHEN tl.result = 'SMART_SKIPPED' THEN 1 ELSE 0 END) AS smart_skipped,
  SUM(CASE WHEN tl.result <> 'FAILED' THEN 1 ELSE 0 END) AS success,
  (CASE WHEN COUNT(*) > 0 THEN
    CAST(SUM(CASE WHEN tl.result <> 'FAILED' THEN 1 ELSE 0 END) AS FLOAT) / COUNT(*)
  ELSE 1 END) AS successful
FROM raw_data.test_launches tl
JOIN raw_data.test_definitions td ON td.id = tl.test_definition_id
GROUP BY tl.test_session_id, td.path;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_test_sessions_v2 AS
    SELECT
        ts.id AS test_session_id,
        ts.group_id,
        ts.test_task_id,
        ts.started_at,
        COUNT(*) AS tests,
        (CASE WHEN SUM(CASE WHEN tl.result = 'FAILED' THEN 1 ELSE 0 END) > 0 THEN 'FAILED' ELSE 'PASSED' END) AS result,
        SUM(tl.duration) AS duration,
        SUM(tl.failed) AS failed,
        SUM(tl.passed) AS passed,
        SUM(tl.skipped) AS skipped,
        SUM(tl.smart_skipped) AS smart_skipped,
        SUM(tl.success) AS success,
        (CASE WHEN COUNT(*) > 0 THEN
			CAST(SUM(tl.success) AS FLOAT) / COUNT(*)
		 ELSE 1 END) AS successful
    FROM raw_data.test_sessions ts
    LEFT JOIN raw_data.view_test_launches tl ON ts.id = tl.test_session_id
    GROUP BY ts.id;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_tested_methods AS
    SELECT
        builds.group_id,
        builds.app_id,
		methods.build_id,
		methods.signature,
        methods.body_checksum,
		launches.id AS test_launch_id,
		launches.test_definition_id,
		launches.test_session_id,
		builds.branch,
		instances.env_id,
		sessions.test_task_id,
		definitions.tags AS test_tags,
		sessions.started_at AS session_started_at,
		builds.created_at AS build_created_at
    FROM raw_data.coverage coverage
	JOIN raw_data.methods methods ON methods.classname = coverage.classname
    JOIN raw_data.instances instances ON instances.id = coverage.instance_id
    JOIN raw_data.builds builds ON builds.id = methods.build_id AND builds.id = instances.build_id
    JOIN raw_data.test_launches launches ON launches.id = coverage.test_id
	JOIN raw_data.test_sessions sessions ON sessions.id = launches.test_session_id
	JOIN raw_data.test_definitions definitions ON definitions.id = launches.test_definition_id
    WHERE TRUE
	  AND methods.probes_count > 0
	  AND launches.result = 'PASSED'
	  AND BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) > 0
      AND BIT_LENGTH(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) = methods.probes_count;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_tested_builds_comparison AS
WITH
	TargetMethods AS (
    	SELECT
			target.build_id,
            target.signature,
            target.body_checksum,
            builds.created_at AS build_created_at
	 	FROM raw_data.view_methods_with_rules target
	 	JOIN raw_data.builds builds ON builds.id = target.build_id
  	),
	TestedMethods AS (
		SELECT
		    tested.test_launch_id,
            (target.body_checksum <> tested.body_checksum) AS has_changed_methods,
			target.build_id AS target_build_id,
			tested.build_id AS tested_build_id,
			tested.env_id
		FROM raw_data.view_tested_methods tested
		JOIN TargetMethods target ON target.signature = tested.signature
		WHERE TRUE
		  --filter by chronological order
          AND tested.build_created_at <= target.build_created_at
  	)
SELECT
	test_launch_id,
	BOOL_OR(has_changed_methods) AS has_changed_methods,
	target_build_id,
	tested_build_id,
	env_id
FROM TestedMethods
GROUP BY test_launch_id, target_build_id, tested_build_id, env_id;

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_builds AS
SELECT
  b.id AS build_id,
  b.group_id,
  b.app_id,
  split_part(b.id, ':', 3) AS version_id,
  (SELECT STRING_AGG(DISTINCT env_id, ', ') from raw_data.instances where env_id != '' and build_id = b.id) AS envs,
  b.build_version,
  b.commit_sha,
  b.branch,
  b.commit_date,--deprecated
  b.commit_author,
  b.commit_message,
  b.created_at AS created_at,
  COUNT(DISTINCT m.classname) AS total_classes,
  COUNT(*) AS total_methods,
  SUM(m.probes_count) AS total_probes,
  COALESCE(b.committed_at, b.created_at) AS committed_at
FROM raw_data.builds b
JOIN raw_data.view_methods_with_rules m ON b.id = m.build_id
GROUP BY b.id;
