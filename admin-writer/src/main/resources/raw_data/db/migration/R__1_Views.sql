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
        app_id
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
        coverage.created_at
    FROM raw_data.coverage coverage
	JOIN raw_data.methods methods ON methods.classname = coverage.classname
    JOIN raw_data.instances instances ON instances.id = coverage.instance_id
    JOIN raw_data.builds builds ON builds.id = methods.build_id AND builds.id = instances.build_id
    LEFT JOIN raw_data.test_launches launches ON launches.id = coverage.test_id
	LEFT JOIN raw_data.test_definitions definitions ON definitions.id = launches.test_definition_id
    WHERE TRUE
	  AND methods.probes_count > 0
	  AND BIT_COUNT(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) > 0
      AND BIT_LENGTH(SUBSTRING(coverage.probes FROM methods.probe_start_pos + 1 FOR methods.probes_count)) = methods.probes_count;