-----------------------------------------------------------------

-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_methods_coverage_v2 CASCADE;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_methods_coverage_v2 AS
    SELECT
        MIN(group_id) AS group_id,
        MIN(app_id) AS app_id,
		build_id,
		env_id,
		MIN(branch) AS branch,
		test_tags,
        signature,
        body_checksum,
        probes_count,
        BIT_OR(probes) AS probes,
        BIT_COUNT(BIT_OR(probes)) AS covered_probes,
        MAX(created_at) AS created_at,
        MIN(build_created_at) AS build_created_at
    FROM raw_data.view_methods_coverage_v2
	GROUP BY signature, body_checksum, probes_count, build_id, env_id, test_tags;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_methods_coverage_v2_pk ON raw_data.matview_methods_coverage_v2 (signature, body_checksum, probes_count, build_id, env_id, test_tags);
CREATE INDEX ON raw_data.matview_methods_coverage_v2(group_id, app_id);
CREATE INDEX ON raw_data.matview_methods_coverage_v2(build_id);
CREATE INDEX ON raw_data.matview_methods_coverage_v2(signature, body_checksum, probes_count);

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
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_builds_coverage CASCADE;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_builds_coverage AS
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
	FROM raw_data.view_methods_with_rules methods
	JOIN raw_data.builds builds ON builds.id = methods.build_id
	ORDER BY methods.build_id, methods.signature
  ),
  Builds AS (
	SELECT
		methods.build_id,
		SUM(methods.probes_count) AS total_probes,
		COUNT(*) AS total_methods
	FROM raw_data.view_methods_with_rules methods
	GROUP BY methods.build_id
  ),
  CoverageGroupedByMethods AS (
	SELECT
		methods.build_id AS target_build_id,
		methods.signature,
		methods.probes_count,
		methods.probes_start,
		methods.method_num,
		coverage.build_id AS coverage_build_id,
		coverage.env_id,
		coverage.test_tags,
		BIT_OR(coverage.probes) AS probes
	FROM raw_data.matview_methods_coverage_v2 coverage
	JOIN Methods methods ON coverage.signature = methods.signature
	  AND coverage.body_checksum = methods.body_checksum
	  AND coverage.probes_count = methods.probes_count
	  AND coverage.group_id = methods.group_id
	  AND coverage.app_id = methods.app_id
	  --filter by chronological order
	  AND coverage.build_created_at <= methods.build_created_at
   	GROUP BY methods.build_id, coverage.build_id, coverage.env_id, coverage.test_tags, methods.signature, methods.probes_count, methods.probes_start, methods.method_num
	ORDER BY methods.build_id, coverage.build_id, coverage.env_id, coverage.test_tags, methods.signature
  ),
  CoverageGroupedByBuilds AS (
	SELECT
		coverage.target_build_id,
		coverage.coverage_build_id,
		coverage.env_id,
		coverage.test_tags,
		raw_data.CONCAT_VARBIT(coverage.probes::VARBIT, coverage.probes_start::INT) AS probes,
		raw_data.CONCAT_VARBIT(CASE
			WHEN BIT_COUNT(coverage.probes) > 0
			THEN REPEAT('1', 1)::VARBIT
			ELSE REPEAT('0', 1)::VARBIT
		END, coverage.method_num::INT) AS tested_methods
	FROM CoverageGroupedByMethods coverage
	GROUP BY coverage.target_build_id, coverage.coverage_build_id, coverage.env_id, coverage.test_tags
  ),
  AugmentedCoverageGroupedByBuilds AS (
	SELECT
		coverage.target_build_id,
		coverage.coverage_build_id,
		coverage.env_id,
		coverage.test_tags,
		coverage.probes || (REPEAT('0', (builds.total_probes - BIT_LENGTH(coverage.probes))::INT)::VARBIT) AS probes,
		coverage.tested_methods || REPEAT('0', (builds.total_methods - BIT_LENGTH(coverage.tested_methods))::INT)::VARBIT AS tested_methods,
		builds.total_probes,
		builds.total_methods
	FROM CoverageGroupedByBuilds coverage
	JOIN Builds builds ON builds.build_id = coverage.target_build_id
	WHERE BIT_COUNT(coverage.probes) > 0
  )
  SELECT
  	coverage.target_build_id AS build_id,
	coverage.coverage_build_id AS coverage_build_id,
	coverage_builds.branch,
	coverage.env_id,
	coverage.test_tags,
	coverage.total_probes,
	coverage.probes,
	coverage.total_methods,
	coverage.tested_methods
  FROM AugmentedCoverageGroupedByBuilds coverage
  JOIN raw_data.builds coverage_builds ON coverage_builds.id = coverage.coverage_build_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_builds_coverage_pk ON raw_data.matview_builds_coverage (build_id, coverage_build_id, env_id, branch, test_tags);

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
  	commit_date,
  	commit_author,
  	commit_message,
  	created_at,
  	total_classes,
  	total_methods,
  	total_probes
FROM raw_data.view_builds;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_builds_pk ON raw_data.matview_builds (build_id);