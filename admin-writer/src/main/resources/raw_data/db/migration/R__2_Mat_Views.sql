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
    probe_start_pos
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