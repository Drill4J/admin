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
-- package coverage
SELECT
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
GROUP BY package_name
ORDER BY
    ratio_percentage;
*/

/*
-- class coverage
SELECT
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
    class_name
-- ORDER BY set_bits_percentage ASC -- order by coverage amount
* */

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
* */