SELECT
    m.group_id,
    m.app_id,
    m.build_id,
    m.signature,
    m.body_checksum,
    m.probes_count,
    md5(m.signature||':'||m.body_checksum||':'||m.probes_count) as method_id,
    m.name AS method_name,
    m.classname AS class_name,
    m.params AS method_params,
    m.return_type,
    null AS probes_start,--deprecated field
	null AS method_num,--deprecated field
    DATE_TRUNC('day', m.created_at) AS creation_day,
    m.created_at AS created_at
from raw_data.methods m
WHERE m.created_at >= metrics.get_metrics_period(m.group_id)
   AND m.created_at > :since_timestamp
   AND m.created_at <= :until_timestamp
   AND NOT EXISTS (SELECT 1
       FROM raw_data.method_ignore_rules r
       WHERE r.group_id = m.group_id
        AND r.app_id = m.app_id
        AND (r.name_pattern IS NOT NULL AND m.name::text like r.name_pattern::text
            OR r.classname_pattern IS NOT NULL AND m.classname::text like r.classname_pattern::text
            OR r.annotations_pattern IS NOT NULL AND m.annotations::text like r.annotations_pattern::text
            OR r.class_annotations_pattern IS NOT NULL AND m.class_annotations::text like r.class_annotations_pattern::text))
ORDER BY m.created_at ASC, m.signature