-----------------------------------------------------------------

-----------------------------------------------------------------
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_methods_coverage_v2;

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
DROP MATERIALIZED VIEW IF EXISTS raw_data.matview_tested_builds_comparison;

CREATE MATERIALIZED VIEW IF NOT EXISTS raw_data.matview_tested_builds_comparison AS
SELECT
	test_launch_id,
	has_changed_methods,
	target_build_id,
	tested_build_id,
	env_id
FROM raw_data.view_tested_builds_comparison;

CREATE UNIQUE INDEX IF NOT EXISTS idx_matview_tested_builds_comparison_pk ON raw_data.matview_tested_builds_comparison (test_launch_id, target_build_id, tested_build_id, env_id);