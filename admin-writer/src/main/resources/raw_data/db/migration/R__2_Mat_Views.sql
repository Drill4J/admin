-----------------------------------------------------------------

-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_methods_with_rules CASCADE;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_methods_with_rules AS
SELECT
    signature,
    name,
    classname,
    params,
    return_type,
    body_checksum,
    probes_count,
    build_id,
    group_id,
    app_id,
    probe_start_pos,--deprecated
    probes_start,
    method_num
FROM raw_data.view_methods_with_rules;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_methods_with_rules_pk ON raw_data.matview_methods_with_rules (build_id, signature);

-----------------------------------------------------------------

-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_methods_coverage_v3 CASCADE;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_methods_coverage_v3 AS
    SELECT
        group_id,
        app_id,
		build_id,
		env_id,
		MIN(branch) AS branch,
		test_tags,
		test_session_id,
        signature,
        body_checksum,
        probes_count,
        BIT_OR(probes) AS probes,
        MIN(build_created_at) AS build_created_at
    FROM raw_data.view_methods_coverage_v2
	GROUP BY group_id, app_id, signature, body_checksum, probes_count, build_id, env_id, test_tags, test_session_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_methods_coverage_v3_pk ON raw_data.matview_methods_coverage_v3 (signature, body_checksum, probes_count, build_id, env_id, test_tags, test_session_id);
CREATE INDEX ON raw_data.matview_methods_coverage_v3(group_id, app_id);
CREATE INDEX ON raw_data.matview_methods_coverage_v3(build_id);
CREATE INDEX ON raw_data.matview_methods_coverage_v3(signature, body_checksum, probes_count);

-----------------------------------------------------------------

-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_tested_builds_comparison CASCADE;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_tested_builds_comparison AS
SELECT
	test_launch_id,
	has_changed_methods,
	target_build_id,
	tested_build_id,
	env_id
FROM raw_data.view_tested_builds_comparison;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_tested_builds_comparison_pk ON raw_data.matview_tested_builds_comparison (test_launch_id, target_build_id, tested_build_id, env_id);

-----------------------------------------------------------------

-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_builds_coverage_v3 CASCADE;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_builds_coverage_v3 AS
WITH
  Methods AS (
	SELECT
	    builds.group_id,
		builds.app_id,
		methods.build_id,
		methods.signature,
		methods.body_checksum,
		methods.probes_count,
		(SUM(methods.probes_count) OVER (PARTITION BY methods.build_id ORDER BY methods.signature)) - methods.probes_count + 1 AS probes_start,
		(COUNT(*) OVER (PARTITION BY methods.build_id ORDER BY methods.signature)) AS method_num,
		builds.created_at AS build_created_at
	FROM raw_data.matview_methods_with_rules methods
	JOIN raw_data.builds builds ON builds.id = methods.build_id
	ORDER BY methods.build_id, methods.signature
  ),
  Builds AS (
	SELECT
		methods.build_id,
		SUM(methods.probes_count) AS total_probes,
		COUNT(*) AS total_methods
	FROM raw_data.matview_methods_with_rules methods
	GROUP BY methods.build_id
  ),
  MethodsCoverage AS (
	SELECT
		methods.build_id,
		methods.signature,
		methods.probes_count,
		methods.probes_start,
		methods.method_num,
		coverage.build_id AS coverage_build_id,
		coverage.env_id,
		coverage.test_session_id,
		coverage.test_tags,
		BIT_OR(coverage.probes) AS probes
	FROM raw_data.matview_methods_coverage_v3 coverage
	JOIN Methods methods ON coverage.group_id = methods.group_id
	    AND coverage.app_id = methods.app_id
		AND coverage.signature = methods.signature
	  	AND coverage.body_checksum = methods.body_checksum
	  	AND coverage.probes_count = methods.probes_count
	--filter by chronological order
	WHERE coverage.build_created_at <= methods.build_created_at
	GROUP BY methods.build_id, coverage.build_id, coverage.env_id, coverage.test_session_id, coverage.test_tags, methods.signature, methods.probes_count, methods.probes_start, methods.method_num
	ORDER BY methods.build_id, coverage.build_id, coverage.env_id, coverage.test_session_id, coverage.test_tags, methods.signature
  ),
  BuildsCoverage AS (
	SELECT
		coverage.build_id,
		coverage.coverage_build_id,
		coverage.env_id,
		coverage.test_session_id,
		coverage.test_tags,
		raw_data.CONCAT_VARBIT(coverage.probes::VARBIT, coverage.probes_start::INT) AS probes,
		raw_data.CONCAT_VARBIT(REPEAT('1', 1)::VARBIT, coverage.method_num::INT) AS methods_probes
	FROM MethodsCoverage coverage
	GROUP BY coverage.build_id, coverage.coverage_build_id, coverage.env_id, coverage.test_session_id, coverage.test_tags
  ),
  PaddedBuildsCoverage AS (
	SELECT
		coverage.build_id,
		coverage.coverage_build_id,
		coverage.env_id,
		coverage.test_session_id,
		coverage.test_tags,
		coverage.probes || (REPEAT('0', (builds.total_probes - BIT_LENGTH(coverage.probes))::INT)::VARBIT) AS probes,
		coverage.methods_probes || REPEAT('0', (builds.total_methods - BIT_LENGTH(coverage.methods_probes))::INT)::VARBIT AS methods_probes,
		builds.total_probes,
		builds.total_methods
	FROM BuildsCoverage coverage
	JOIN Builds builds ON builds.build_id = coverage.build_id
  )
  SELECT
  	coverage.build_id,
	coverage.coverage_build_id,
	coverage_builds.branch,
	coverage.env_id,
	coverage.test_session_id,
	coverage.test_tags,
	coverage.probes,
	coverage.methods_probes,
	coverage.total_probes,
	coverage.total_methods
  FROM PaddedBuildsCoverage coverage
  JOIN raw_data.builds coverage_builds ON coverage_builds.id = coverage.coverage_build_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_builds_coverage_v3_pk ON raw_data.matview_builds_coverage_v3 (build_id, coverage_build_id, env_id, test_session_id, test_tags);

-----------------------------------------------------------------

-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_builds CASCADE;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_builds AS
SELECT
    build_id,
  	group_id,
  	app_id,
  	version_id,
  	envs,
  	build_version,
  	commit_sha,
  	branch,
  	commit_date,--deprecated
  	commit_author,
  	commit_message,
  	created_at,
  	total_classes,
  	total_methods,
  	total_probes,
  	committed_at
FROM raw_data.view_builds;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_builds_pk ON raw_data.matview_builds (build_id);

-----------------------------------------------------------------

-----------------------------------------------------------------
CREATE OR REPLACE VIEW raw_data.view_test_session_coverage AS
WITH
	SessionsCoverage AS (
		SELECT
			coverage.build_id,
			coverage.test_session_id,
			BIT_OR(probes) AS probes,
			BIT_OR(CASE WHEN coverage.build_id = coverage.coverage_build_id THEN probes ELSE NULL END) AS isolated_probes,
			MIN(sessions.started_at) AS session_started_at
		FROM raw_data.matview_builds_coverage_v3 coverage
		JOIN raw_data.builds coverage_builds ON coverage_builds.id = coverage.coverage_build_id
		JOIN raw_data.test_sessions sessions ON sessions.id = coverage.test_session_id
		GROUP BY coverage.build_id, coverage.test_session_id
	),
	SessionsWithoutCoverage AS (
		SELECT
			tsb.build_id,
			sessions.id AS test_session_id,
			NULL::VARBIT AS probes,
			NULL::VARBIT AS isolated_probes,
			sessions.started_at AS session_started_at
		FROM raw_data.test_sessions sessions
		JOIN raw_data.test_session_builds tsb ON tsb.test_session_id = sessions.id
		GROUP BY tsb.build_id, sessions.id
	),
	SessionsWithAndWithoutCoverage AS (
		SELECT
			coverage.build_id,
			coverage.test_session_id,
			BIT_OR(coverage.probes) AS probes,
			BIT_OR(coverage.isolated_probes) AS isolated_probes,
			MIN(coverage.session_started_at) AS session_started_at
		FROM (
			SELECT * FROM SessionsCoverage
			UNION ALL
			SELECT * FROM SessionsWithoutCoverage
		) coverage
		GROUP BY coverage.build_id, coverage.test_session_id
	),
  	AccumulatedSessionsCoverage AS (
		SELECT
			coverage.build_id,
		    coverage.test_session_id,
			coverage.session_started_at,

			coverage.probes,
			BIT_OR(coverage.probes) OVER (
				PARTITION BY coverage.build_id
				ORDER BY coverage.session_started_at
			) AS accumulated_probes,
			COALESCE(coverage.probes & ~(BIT_OR(coverage.probes) OVER (
				PARTITION BY coverage.build_id
				ORDER BY coverage.session_started_at
				ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
			)), coverage.probes) AS unique_probes,

			coverage.isolated_probes,
			BIT_OR(coverage.isolated_probes) OVER (
				PARTITION BY coverage.build_id
				ORDER BY coverage.session_started_at
			) AS isolated_accumulated_probes,
			COALESCE(coverage.isolated_probes & ~(BIT_OR(coverage.isolated_probes) OVER (
				PARTITION BY coverage.build_id
				ORDER BY coverage.session_started_at
				ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
			)), coverage.probes) AS isolated_unique_probes

		FROM SessionsWithAndWithoutCoverage coverage
  	)
SELECT
    coverage.build_id,
    coverage.test_session_id,
	builds.total_probes,

    COALESCE(BIT_COUNT(coverage.probes), 0) AS covered_probes,
    COALESCE(BIT_COUNT(coverage.unique_probes), 0) AS unique_covered_probes,
    COALESCE(BIT_COUNT(coverage.accumulated_probes), 0) AS accumulated_covered_probes,
    COALESCE(CAST(COALESCE(BIT_COUNT(coverage.probes), 0) AS FLOAT) / builds.total_probes, 0.0) AS probes_coverage_ratio,
    COALESCE(CAST(COALESCE(BIT_COUNT(coverage.unique_probes), 0) AS FLOAT) / builds.total_probes, 0.0) AS unique_probes_coverage_ratio,
    COALESCE(CAST(COALESCE(BIT_COUNT(coverage.accumulated_probes), 0) AS FLOAT) / builds.total_probes, 0.0) AS accumulated_probes_coverage_ratio,

	COALESCE(BIT_COUNT(coverage.isolated_probes), 0) AS isolated_covered_probes,
    COALESCE(BIT_COUNT(coverage.isolated_unique_probes), 0) AS isolated_unique_covered_probes,
    COALESCE(BIT_COUNT(coverage.isolated_accumulated_probes), 0) AS isolated_accumulated_covered_probes,
    COALESCE(CAST(COALESCE(BIT_COUNT(coverage.isolated_probes), 0) AS FLOAT) / builds.total_probes, 0.0) AS isolated_probes_coverage_ratio,
    COALESCE(CAST(COALESCE(BIT_COUNT(coverage.isolated_unique_probes), 0) AS FLOAT) / builds.total_probes, 0.0) AS isolated_unique_probes_coverage_ratio,
    COALESCE(CAST(COALESCE(BIT_COUNT(coverage.isolated_accumulated_probes), 0) AS FLOAT) / builds.total_probes, 0.0) AS isolated_accumulated_probes_coverage_ratio

FROM AccumulatedSessionsCoverage coverage
JOIN raw_data.view_builds builds ON builds.build_id = coverage.build_id
ORDER BY coverage.session_started_at;

-----------------------------------------------------------------

-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_test_session_build_coverage CASCADE;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_test_session_build_coverage AS
SELECT
	s.test_session_id,
	s.group_id,
	s.test_task_id,
	s.started_at,
	s.tests,
	s.result,
	s.duration,
	s.failed,
	s.passed,
	s.skipped,
	s.smart_skipped,
	s.success,
	s.successful,
	sb.build_id,
	sb.build_tests,
	sc.covered_probes,
	sc.unique_covered_probes,
	sc.accumulated_covered_probes,
	sc.probes_coverage_ratio,
	sc.unique_probes_coverage_ratio,
	sc.accumulated_probes_coverage_ratio,
	sc.isolated_covered_probes,
	sc.isolated_unique_covered_probes,
	sc.isolated_accumulated_covered_probes,
	sc.isolated_probes_coverage_ratio,
	sc.isolated_unique_probes_coverage_ratio,
	sc.isolated_accumulated_probes_coverage_ratio
FROM raw_data.view_test_sessions s
LEFT JOIN raw_data.view_test_session_builds_v2 sb ON sb.test_session_id = s.test_session_id
LEFT JOIN raw_data.view_test_session_coverage sc ON sc.test_session_id = s.test_session_id AND sc.build_id = sb.build_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_test_session_build_coverage_pk ON raw_data.matview_test_session_build_coverage (test_session_id, build_id);

-----------------------------------------------------------------

-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_builds_comparison CASCADE;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_builds_comparison AS
WITH
    BaselineMethods AS (
        SELECT
            m.group_id,
            m.app_id,
            m.build_id,
            m.signature,
            m.body_checksum,
            m.probes_count
        FROM raw_data.matview_methods_with_rules m
    ),
    TargetMethods AS (
        SELECT
            m.group_id,
            m.app_id,
            m.build_id,
            m.signature,
            m.body_checksum,
            m.probes_count,
            (SUM(m.probes_count) OVER (PARTITION BY m.build_id ORDER BY m.signature)) - m.probes_count + 1 AS probes_start,
            (COUNT(*) OVER (PARTITION BY m.build_id ORDER BY m.signature)) AS method_num
        FROM raw_data.matview_methods_with_rules m
    ),
    MethodsComparison AS (
        SELECT
            m.build_id,
            m.signature,
            m.body_checksum,
            m.probes_count,
            m.probes_start,
            m.method_num,
            baseline.build_id AS baseline_build_id,
            baseline.body_checksum AS baseline_body_checksum,
            (CASE WHEN m.body_checksum = baseline.body_checksum AND m.probes_count = baseline.probes_count THEN 1 ELSE 0 END) AS equal,
            (CASE WHEN m.body_checksum <> baseline.body_checksum OR m.probes_count <> baseline.probes_count THEN 1 ELSE 0 END) AS modified
        FROM TargetMethods m
        JOIN BaselineMethods baseline ON baseline.group_id = m.group_id
            AND baseline.app_id = m.app_id
            AND baseline.signature = m.signature
        JOIN raw_data.builds target_builds ON target_builds.id = m.build_id
        JOIN raw_data.builds baseline_builds ON baseline_builds.id = baseline.build_id
        WHERE baseline.build_id <> m.build_id
            --filter by chronology
            AND baseline_builds.created_at <= target_builds.created_at
        ORDER BY m.build_id, baseline.build_id, m.signature
    ),
    BuildsComparison AS (
        SELECT
            m.build_id,
            m.baseline_build_id,
            SUM(m.equal) AS equal,
            SUM(m.modified) AS modified,
            raw_data.CONCAT_VARBIT(REPEAT(m.equal::VARCHAR, m.probes_count)::VARBIT, m.probes_start::INT) AS probes,
            raw_data.CONCAT_VARBIT(REPEAT(m.equal::VARCHAR, 1)::VARBIT, m.method_num::INT) AS methods_probes
        FROM MethodsComparison m
        GROUP BY m.build_id, m.baseline_build_id
    ),
    PaddedBuildsComparison AS (
        SELECT
            m.build_id,
            m.baseline_build_id,
            m.equal,
            m.modified,
            target_b.total_methods - m.modified - m.equal AS added,
            baseline_b.total_methods - m.modified - m.equal AS deleted,
            target_b.total_methods AS total_methods,
            target_b.total_probes AS total_probes,
            baseline_b.total_methods AS baseline_total_methods,
            baseline_b.total_probes AS baseline_total_probes,
            m.probes || (REPEAT('0', (target_b.total_probes - BIT_LENGTH(m.probes))::INT)::VARBIT) AS probes,
            m.methods_probes || REPEAT('0', (target_b.total_methods - BIT_LENGTH(m.methods_probes))::INT)::VARBIT AS methods_probes
        FROM BuildsComparison m
        JOIN raw_data.matview_builds target_b ON target_b.build_id = m.build_id
        JOIN raw_data.matview_builds baseline_b ON baseline_b.build_id = m.baseline_build_id
    )
    SELECT
        builds.build_id,
        builds.baseline_build_id,
        builds.equal,
        builds.modified,
        builds.added,
        builds.deleted,
        builds.total_methods,
        builds.total_probes,
        builds.baseline_total_methods,
        builds.baseline_total_probes,
        (builds.equal::FLOAT / builds.total_methods::FLOAT) AS identity_ratio,
        builds.probes AS probes,
        builds.methods_probes AS methods_probes
    FROM PaddedBuildsComparison builds;