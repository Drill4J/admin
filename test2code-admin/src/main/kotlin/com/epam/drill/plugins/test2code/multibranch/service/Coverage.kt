package com.epam.drill.plugins.test2code.multibranch.service

import com.epam.drill.plugins.test2code.multibranch.repository.RawDataRepositoryImpl

suspend fun versionCoverage(agentId: String, buildVersion: String) {
    val agent = RawDataRepositoryImpl.getAgentConfigs(agentId, buildVersion)
    val ast = RawDataRepositoryImpl.getAstEntities(agentId, buildVersion)
    val coverage = RawDataRepositoryImpl.getRawCoverageData(agentId, buildVersion)

    coverage.groupBy { it.className }
    println("${agent.size} ${ast.size} ${coverage.size}")
}

/*

-- method coverage
WITH AggregatedData AS (
    SELECT
        instance_id,
        class_name,
        BIT_OR(probes) AS or_result
    FROM
        auth.exec_class_data
--     WHERE class_name LIKE '%io/spring/api/ArticleApi%' -- filter by class
    GROUP BY
        instance_id, class_name
)
SELECT
	am.probe_start_pos,
	am.probes_count,
	am.name,
	--                                                        + 1 because SUBSTRING indexes are 1-based, but our probes pos are 0-based
    (BIT_COUNT(SUBSTRING(ad.or_result FROM am.probe_start_pos + 1 FOR am.probes_count)) * 100.0 / LENGTH(SUBSTRING(ad.or_result FROM am.probe_start_pos + 1 FOR am.probes_count))) AS set_bits_ratio,
	SUBSTRING(ad.or_result FROM am.probe_start_pos + 1 FOR am.probes_count) AS substr_probes,
    am.*,
    ad.*
FROM
    auth.ast_method am
JOIN
    AggregatedData ad ON am.instance_id = ad.instance_id AND am.class_name = ad.class_name
-- WHERE am.class_name LIKE '%io/spring/api/ArticleApi%'; -- filter by class
ORDER BY set_bits_ratio ASC


-- class coverage
SELECT
	instance_id,
    class_name,
    BIT_COUNT(BIT_OR(probes)) * 100.0 / LENGTH(BIT_OR(probes)::VARBIT) AS set_bits_percentage,
    -- not important for final metrics
    COUNT(*) AS entry_count,
    BIT_COUNT(BIT_OR(probes)) AS set_bits_count,
    LENGTH(BIT_OR(probes)::VARBIT) AS result_length,
    BIT_OR(probes) AS or_result
FROM
    auth.exec_class_data
-- WHERE class_name like '%ArticleFavoriteCount%' -- filter by class
GROUP BY
    instance_id, class_name
ORDER BY set_bits_percentage ASC -- order by coverage amount


-- package coverage
SELECT
    instance_id,
    package_name,
    (SUM(set_bits_count) * 100.0 / NULLIF(SUM(result_length), 0)) AS ratio_percentage,
    SUM(set_bits_count) AS total_set_bits_count,
    SUM(result_length) AS total_result_length
FROM (
    SELECT
        instance_id,
        REVERSE(
            SUBSTRING(
                REVERSE(class_name) FROM POSITION('/' IN REVERSE(class_name)) + 1)) AS package_name,
        class_name,
        BIT_COUNT(BIT_OR(probes)) AS set_bits_count,
        LENGTH(BIT_OR(probes)::VARBIT) AS result_length
	FROM
        auth.exec_class_data
	GROUP BY
    	instance_id, class_name, package_name
) AS subquery
-- WHERE package_name like '%io/spring%' -- filter by package name
GROUP BY package_name, instance_id
ORDER BY
    instance_id, ratio_percentage;


-- total coverage
SELECT
    instance_id,
    (SUM(set_bits_count) * 100.0 / NULLIF(SUM(result_length), 0)) AS ratio_percentage,
    SUM(set_bits_count) AS total_set_bits_count,
    SUM(result_length) AS total_result_length
FROM (
    SELECT
        instance_id,
        class_name,
        BIT_COUNT(BIT_OR(probes)) AS set_bits_count,
        LENGTH(BIT_OR(probes)::VARBIT) AS result_length
	FROM
        auth.exec_class_data
	GROUP BY
    	instance_id, class_name
) AS subquery
GROUP BY instance_id
ORDER BY
    ratio_percentage;


-- risks - find new methods
SELECT *
FROM auth.ast_method AS q2
WHERE instance_id = '47126667-67e5-44a9-af37-cd30d28a24e7'
AND NOT EXISTS (
    SELECT 1
    FROM auth.ast_method AS q1
    WHERE q1.instance_id = '6702dabf-a53c-40ee-a3f7-7bfbc7192568'
    AND q1.class_name = q2.class_name
    AND q1.name = q2.name
    AND q1.params = q2.params
    AND q1.return_type = q2.return_type
);


-- risks - find modified methods

SELECT *
FROM auth.ast_method AS q2
WHERE instance_id = 'a09bae5d-63b8-4507-bddf-125ec7d577e5'
AND EXISTS (
    SELECT 1
    FROM auth.ast_method AS q1
    WHERE q1.instance_id = 'e5763ca2-d47f-47a3-b257-608db197feac'
    AND q1.class_name = q2.class_name
    AND q1.name = q2.name
    AND q1.params = q2.params
    AND q1.return_type = q2.return_type
	AND q1.body_checksum <> q2.body_checksum
);

-- risks coverage

WITH AggregatedData AS (
    SELECT
        instance_id,
        class_name,
        BIT_OR(probes) AS or_result
    FROM
        auth.exec_class_data
    WHERE instance_id='8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
    GROUP BY
        instance_id, class_name
)
SELECT
	am.instance_id,
	ad.instance_id,
	am.probe_start_pos,
	am.probes_count,
	am.name,
	am.class_name,
    (BIT_COUNT(SUBSTRING(ad.or_result FROM am.probe_start_pos + 1 FOR am.probes_count)) * 100.0 / LENGTH(SUBSTRING(ad.or_result FROM am.probe_start_pos + 1 FOR am.probes_count))) AS set_bits_ratio,
	SUBSTRING(ad.or_result FROM am.probe_start_pos + 1 FOR am.probes_count) AS substr_probes
FROM
    (
         -- could be replaced with "optimized" version (see bellow)
		SELECT *
		FROM auth.ast_method AS q2
		WHERE instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
		AND NOT EXISTS (
			SELECT 1
			FROM auth.ast_method AS q1
			WHERE q1.instance_id = '7f553ee8-0842-44c8-b488-7a80cff763e5'
			AND q1.class_name = q2.class_name
			AND q1.name = q2.name
			AND q1.params = q2.params
			AND q1.return_type = q2.return_type
		)
		UNION ALL
		SELECT *
		FROM auth.ast_method AS q2
		WHERE instance_id =  '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
		AND EXISTS (
			SELECT 1
			FROM auth.ast_method AS q1
			WHERE q1.instance_id =  '7f553ee8-0842-44c8-b488-7a80cff763e5'
			AND q1.class_name = q2.class_name
			AND q1.name = q2.name
			AND q1.params = q2.params
			AND q1.return_type = q2.return_type
			AND q1.body_checksum <> q2.body_checksum
		)
	) as am
LEFT JOIN
    AggregatedData ad ON am.instance_id = ad.instance_id AND am.class_name = ad.class_name
WHERE am.instance_id='8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
ORDER BY set_bits_ratio ASC

-- all risks "optimized" version (TODO check if it works correctly)
SELECT q2.*
FROM auth.ast_method AS q2
LEFT JOIN auth.ast_method AS q1
    ON q1.instance_id = '7f553ee8-0842-44c8-b488-7a80cff763e5'
    AND q1.class_name = q2.class_name
    AND q1.name = q2.name
    AND q1.params = q2.params
    AND q1.return_type = q2.return_type
WHERE q2.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
  AND (
    (q1.instance_id IS NULL) OR
    (q1.body_checksum <> q2.body_checksum)
  );


-- associated tests
SELECT
	am.class_name,
	am.name,
	ARRAY_AGG(DISTINCT ed.test_id) AS test_ids,
    am.*
FROM
    auth.ast_method am
JOIN
    auth.exec_class_data ed ON
		am.class_name = ed.class_name
    AND am.instance_id = ed.instance_id
WHERE
    am.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
	AND ed.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
    AND BIT_COUNT(SUBSTRING(ed.probes FROM am.probe_start_pos + 1 FOR am.probes_count)) > 0
GROUP BY
    am.id, am.instance_id, am.class_name, am.name, am.params, am.return_type, am.body_checksum, am.probe_start_pos, am.probes_count

-- covered methods
SELECT
    ed.test_id,
	am.id as method_id,
	am.class_name,
    am.name
FROM
    auth.ast_method am
JOIN
    auth.exec_class_data ed ON
        am.class_name = ed.class_name
    AND am.instance_id = ed.instance_id
WHERE
    am.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
    AND ed.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
    AND BIT_COUNT(SUBSTRING(ed.probes FROM am.probe_start_pos + 1 FOR am.probes_count)) > 0
GROUP BY
    ed.test_id, am.id, am.instance_id, am.class_name, am.name, am.params, am.return_type
ORDER BY ed.test_id

-- recommended tests
SELECT
    tm.name AS test_name,
    am.class_name,
    am.name,
    ed.test_id,
    am.*
FROM
    auth.ast_method am
JOIN
    auth.exec_class_data ed ON
        am.class_name = ed.class_name
    AND am.instance_id = ed.instance_id
LEFT JOIN
    auth.test_metadata tm ON ed.test_id = tm.test_id
WHERE
    am.instance_id = '7f553ee8-0842-44c8-b488-7a80cff763e5'
    AND ed.instance_id = '7f553ee8-0842-44c8-b488-7a80cff763e5'
    AND BIT_COUNT(SUBSTRING(ed.probes FROM am.probe_start_pos + 1 FOR am.probes_count)) > 0
    AND EXISTS (
        SELECT 1
        FROM auth.ast_method AS q2
        LEFT JOIN auth.ast_method AS q1
            ON q1.instance_id = '7f553ee8-0842-44c8-b488-7a80cff763e5'
            AND q1.class_name = q2.class_name
            AND q1.name = q2.name
            AND q1.params = q2.params
            AND q1.return_type = q2.return_type
        WHERE q2.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
          AND q1.body_checksum <> q2.body_checksum
          AND q2.class_name = am.class_name
          AND q2.name = am.name
          AND q2.params = am.params
          AND q2.return_type = am.return_type
    )
    AND EXISTS (
        SELECT 1
        FROM auth.test_metadata tm
        WHERE ed.test_id = tm.test_id
    )
GROUP BY
    am.id, am.instance_id, am.class_name, am.name, am.params, am.return_type, am.body_checksum, am.probe_start_pos, am.probes_count, ed.test_id, tm.name


* */
