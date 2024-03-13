/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.writer.rawdata.service

import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.repository.RawDataRepositoryImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement

suspend fun versionCoverage(agentId: String, buildVersion: String) {
    val agent = RawDataRepositoryImpl.getAgentConfigs(agentId, buildVersion)
    val ast = RawDataRepositoryImpl.getAstEntities(agentId, buildVersion)
    val coverage = RawDataRepositoryImpl.getRawCoverageData(agentId, buildVersion)

    coverage.groupBy { it.className }
    println("${agent.size} ${ast.size} ${coverage.size}")
}

suspend fun getNewRisks(newInstanceId: String, oldInstanceId: String): List<JsonObject> {
    val sqlQuery = risksNewQuery(newInstanceId, oldInstanceId)
    return executeQuery(sqlQuery)
}

// TODO suspend is redundant - but it shouldn't be?
suspend fun executeQuery(sqlQuery: String): List<JsonObject> {
    val result = mutableListOf<JsonObject>()

    RawDataWriterDatabaseConfig.getDataSource()?.connection.use { connection ->
        connection?.prepareStatement(sqlQuery)?.use { preparedStatement ->
            val resultSet = preparedStatement.executeQuery()
            val metaData = resultSet.metaData
            val columnCount = metaData.columnCount

            while (resultSet.next()) {
                val rowObject = buildJsonObject {
                    for (i in 1..columnCount) {
                        val columnName = metaData.getColumnName(i)
                        val columnValue = resultSet.getObject(i)
                        val stringValue = columnValue?.toString()
                        put(columnName, Json.encodeToJsonElement(stringValue))
                    }
                }

                result.add(rowObject)
            }
        }
    }

    return result
}

fun generateHtmlTable(results: List<JsonObject>): String {
    val stringBuilder = StringBuilder()

    stringBuilder.append("<table class=\"border-collapse border border-slate-400\">\n")

    // Header row
    if (results.isNotEmpty()) {
        val firstRow = results[0]
        stringBuilder.append("<tr>")
        for (columnName in firstRow.keys) {
            stringBuilder.append("<th class=\"border border-slate-300 \" >").append(columnName).append("</th>")
        }
        stringBuilder.append("</tr>\n")
    }

    // Data rows
    for (row in results) {
        stringBuilder.append("<tr>")
        for ((_, value) in row) {
            // Escape HTML entities to prevent injection attacks
            val escapedValue = value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            stringBuilder.append("<td>").append(escapedValue).append("</td>")
        }
        stringBuilder.append("</tr>\n")
    }

    stringBuilder.append("</table>")

    return stringBuilder.toString()
}

fun methodCoverage() {
    // language=SQL
    """
        WITH AggregatedData AS (
            SELECT
                instance_id,
                class_name,
                BIT_OR(probes) AS or_result
            FROM
                raw_data.exec_class_data
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
            raw_data.ast_method am
        JOIN
            AggregatedData ad ON am.instance_id = ad.instance_id AND am.class_name = ad.class_name
        -- WHERE am.class_name LIKE '%io/spring/api/ArticleApi%'; -- filter by class
        ORDER BY set_bits_ratio ASC
    """.trimIndent()
}

fun classCoverage() {
    // language=SQL
    """
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
            raw_data.exec_class_data
        -- WHERE class_name like '%ArticleFavoriteCount%' -- filter by class
        GROUP BY
            instance_id, class_name
        ORDER BY set_bits_percentage ASC -- order by coverage amount
    """.trimIndent()
}

fun packageCoverage() {
    // language=SQL
    """
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
                raw_data.exec_class_data
            GROUP BY
                instance_id, class_name, package_name
        ) AS subquery
        -- WHERE package_name like '%io/spring%' -- filter by package name
        GROUP BY package_name, instance_id
        ORDER BY
            instance_id, ratio_percentage;
    """.trimIndent()
}


fun totalCoverage() {
    // language=SQL
    """
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
                raw_data.exec_class_data
            GROUP BY
                instance_id, class_name
        ) AS subquery
        GROUP BY instance_id
        ORDER BY
            ratio_percentage;
    """.trimIndent()
}

fun risksNewQuery(newInstanceId: String, oldInstanceId: String): String {
    // language=SQL
    return """
        SELECT *
        FROM raw_data.ast_method AS q2
        WHERE instance_id = '$newInstanceId'
        AND NOT EXISTS (
            SELECT 1
            FROM raw_data.ast_method AS q1
            WHERE q1.instance_id = '$oldInstanceId'
            AND q1.class_name = q2.class_name
            AND q1.name = q2.name
            AND q1.params = q2.params
            AND q1.return_type = q2.return_type
        );
    """.trimIndent()
}

fun risksModified() {
    // language=SQL
    """
        SELECT *
        FROM raw_data.ast_method AS q2
        WHERE instance_id = 'a09bae5d-63b8-4507-bddf-125ec7d577e5'
        AND EXISTS (
            SELECT 1
            FROM raw_data.ast_method AS q1
            WHERE q1.instance_id = 'e5763ca2-d47f-47a3-b257-608db197feac'
            AND q1.class_name = q2.class_name
            AND q1.name = q2.name
            AND q1.params = q2.params
            AND q1.return_type = q2.return_type
            AND q1.body_checksum <> q2.body_checksum
        );
    """.trimIndent()
}

fun risksCoverage() {
    // language=SQL
    """
        WITH AggregatedData AS (
                SELECT
                    instance_id,
                    class_name,
                    BIT_OR(probes) AS or_result
                FROM
                    raw_data.exec_class_data
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
                    FROM raw_data.ast_method AS q2
                    WHERE instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
                    AND NOT EXISTS (
                        SELECT 1
                        FROM raw_data.ast_method AS q1
                        WHERE q1.instance_id = '7f553ee8-0842-44c8-b488-7a80cff763e5'
                        AND q1.class_name = q2.class_name
                        AND q1.name = q2.name
                        AND q1.params = q2.params
                        AND q1.return_type = q2.return_type
                    )
                UNION ALL
                SELECT *
                FROM raw_data.ast_method AS q2
                WHERE instance_id =  '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
                AND EXISTS (
                    SELECT 1
                    FROM raw_data.ast_method AS q1
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
    """.trimIndent()
}


fun associatedTests() {
    // language=SQL
    """
        SELECT
            am.class_name,
            am.name,
            ARRAY_AGG(DISTINCT ed.test_id) AS test_ids,
            am.*
        FROM
            raw_data.ast_method am
        JOIN
            raw_data.exec_class_data ed ON
                am.class_name = ed.class_name
            AND am.instance_id = ed.instance_id
        WHERE
            am.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
            AND ed.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
            AND BIT_COUNT(SUBSTRING(ed.probes FROM am.probe_start_pos + 1 FOR am.probes_count)) > 0
        GROUP BY
            am.id, am.instance_id, am.class_name, am.name, am.params, am.return_type, am.body_checksum, am.probe_start_pos, am.probes_count
    """.trimIndent()
}


fun coveredMethods() {
    // language=SQL
    """
        SELECT
            ed.test_id,
        	am.id as method_id,
        	am.class_name,
            am.name
        FROM
            raw_data.ast_method am
        JOIN
            raw_data.exec_class_data ed ON
                am.class_name = ed.class_name
            AND am.instance_id = ed.instance_id
        WHERE
            am.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
            AND ed.instance_id = '8ab0bf7a-4e8e-42ab-9013-e1ca60319d9e'
            AND BIT_COUNT(SUBSTRING(ed.probes FROM am.probe_start_pos + 1 FOR am.probes_count)) > 0
        GROUP BY
            ed.test_id, am.id, am.instance_id, am.class_name, am.name, am.params, am.return_type
        ORDER BY ed.test_id
    """.trimIndent()
}


fun recommendedTests() {
    // language=sql
    """
        SELECT
            tm.name AS test_name,
            am.class_name,
            am.name,
            ed.test_id,
            am.*
        FROM
            raw_data.ast_method am
        JOIN
            raw_data.exec_class_data ed ON
                am.class_name = ed.class_name
            AND am.instance_id = ed.instance_id
        LEFT JOIN
            raw_data.test_metadata tm ON ed.test_id = tm.test_id
        WHERE
            am.instance_id = '7f553ee8-0842-44c8-b488-7a80cff763e5'
            AND ed.instance_id = '7f553ee8-0842-44c8-b488-7a80cff763e5'
            AND BIT_COUNT(SUBSTRING(ed.probes FROM am.probe_start_pos + 1 FOR am.probes_count)) > 0
            AND EXISTS (
                SELECT 1
                FROM raw_data.ast_method AS q2
                LEFT JOIN raw_data.ast_method AS q1
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
                FROM raw_data.test_metadata tm
                WHERE ed.test_id = tm.test_id
            )
        GROUP BY
            am.id, am.instance_id, am.class_name, am.name, am.params, am.return_type, am.body_checksum, am.probe_start_pos, am.probes_count, ed.test_id, tm.name
    """.trimIndent()
}


/*
-- Metrics by build version (merged data from all instances)
-- method coverage
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.2.0' AND agent_id = 'spring-realworld-backend'
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    CoverageData AS (
        SELECT
            ecd.class_name,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.class_name
    )
    SELECT
        BuildMethods.class_name,
        BuildMethods.name,
        COALESCE((
            BIT_COUNT(SUBSTRING(CoverageData.or_result FROM BuildMethods.probe_start_pos + 1 FOR BuildMethods.probes_count)) * 100.0
            /
            BuildMethods.probes_count
        ), 0.0) AS set_bits_ratio,
        BuildMethods.signature
    FROM BuildMethods
    LEFT JOIN CoverageData ON BuildMethods.class_name = CoverageData.class_name
    ORDER BY
        BuildMethods.class_name,
        BuildMethods.probe_start_pos,
        set_bits_ratio
    ASC

-- class coverage
 WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
		WHERE build_version = '0.2.0' AND agent_id = 'angular-realworld-frontend'
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
	BuildClasses AS (
		SELECT
			class_name,
			SUM(BuildMethods.probes_count) as probes_count
		FROM BuildMethods
		GROUP BY class_name
	),
	CoverageData AS (
        SELECT
            ecd.class_name,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.class_name
    )
    SELECT
        BuildClasses.class_name,
        COALESCE((BIT_COUNT(CoverageData.or_result) * 100.0 / BuildClasses.probes_count ), 0.0) AS set_bits_ratio
    FROM BuildClasses
    LEFT JOIN CoverageData ON BuildClasses.class_name = CoverageData.class_name
    ORDER BY
        set_bits_ratio
    ASC

-- class coverage - "optimized" version with just the class_names (no extra join to get BuildClasses)
 WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.2.0' AND agent_id = 'angular-realworld-frontend'
    ),
    BuildClassNames AS (
		SELECT class_name
        -- !warning! one cannot simply do SUM(am.probes_count) to get class probe count - bc it'll aggregate dup entries from different instances
		FROM raw_data.ast_method am
		JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
		WHERE probes_count > 0
		GROUP BY class_name
    ),
    CoverageData AS (
        SELECT
            ecd.class_name,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.class_name
    )
    SELECT
        BuildClassNames.class_name,
        COALESCE((BIT_COUNT(CoverageData.or_result) * 100.0 / LENGTH(CoverageData.or_result)), 0.0) AS set_bits_ratio
    FROM BuildClassNames
    LEFT JOIN CoverageData ON BuildClassNames.class_name = CoverageData.class_name
    ORDER BY
        set_bits_ratio
    ASC

-- package coverage
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.2.0' AND agent_id = 'spring-realworld-backend'
    ),
	BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
	BuildClasses AS (
		SELECT
			class_name,
			SUM(BuildMethods.probes_count) as probes_count
		FROM BuildMethods
		GROUP BY class_name
	),
    BuildPackages AS (
        SELECT
            REVERSE(
                SUBSTRING(
                    REVERSE(BuildClasses.class_name) FROM POSITION('/' IN REVERSE(BuildClasses.class_name)) + 1)) package_name,
			SUM(BuildClasses.probes_count) as package_probes_count
        FROM BuildClasses
		GROUP BY package_name
    ),
    CoveredClasses AS (
        SELECT
            REVERSE(
                SUBSTRING(
                    REVERSE(class_name) FROM POSITION('/' IN REVERSE(class_name)) + 1)) AS package_name,
            class_name,
            BIT_COUNT(BIT_OR(probes)) AS set_bits_count
        FROM
            raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY
            class_name, package_name
    )
    SELECT
        BuildPackages.package_name,
        COALESCE(
			(SUM(CoveredClasses.set_bits_count) * 100.0 / BuildPackages.package_probes_count)
		, 0) AS set_bits_ratio
    FROM BuildPackages
    LEFT JOIN CoveredClasses ON BuildPackages.package_name = CoveredClasses.package_name
    GROUP BY BuildPackages.package_name,
			BuildPackages.package_probes_count
    ORDER BY
        set_bits_ratio;

-- total coverage
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
		WHERE build_version = '0.2.0' AND agent_id = 'angular-realworld-frontend'
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
	BuildClasses AS (
		SELECT
			class_name,
			SUM(BuildMethods.probes_count) as probes_count
		FROM BuildMethods
		GROUP BY class_name
	),
	CoveredClasses AS (
        SELECT
            class_name,
            BIT_COUNT(BIT_OR(probes)) AS set_bits_count
        FROM
            raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY
            class_name
    )
    SELECT
        SUM(CoveredClasses.set_bits_count) / SUM(BuildClasses.probes_count)
    FROM BuildClasses
    LEFT JOIN CoveredClasses ON BuildClasses.class_name = CoveredClasses.class_name


-- risks - find new methods
    WITH
    ABuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.1.0' AND agent_id = 'spring-realworld-backend'
    ),
    BBuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.2.0' AND agent_id = 'spring-realworld-backend'
    ),
    ABuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.ast_method am
        JOIN ABuildInstanceIds ON am.instance_id = ABuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    BBuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count
        FROM raw_data.ast_method am
        JOIN BBuildInstanceIds ON am.instance_id = BBuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    )
    SELECT *
    FROM BBuildMethods AS q2
    WHERE NOT EXISTS (
        SELECT 1
        FROM ABuildMethods AS q1
        WHERE q1.signature = q2.signature
    )
    ORDER BY class_name

-- risks - find modified methods

    WITH
    ABuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.1.0' AND agent_id = 'spring-realworld-backend'
    ),
    BBuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.2.0' AND agent_id = 'spring-realworld-backend'
    ),
    ABuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum
        FROM raw_data.ast_method am
        JOIN ABuildInstanceIds ON am.instance_id = ABuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    BBuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum
        FROM raw_data.ast_method am
        JOIN BBuildInstanceIds ON am.instance_id = BBuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    )
    SELECT *
    FROM BBuildMethods AS q2
    WHERE EXISTS (
        SELECT 1
        FROM ABuildMethods AS q1
        WHERE q1.signature = q2.signature
        AND q1.body_checksum <> q2.body_checksum
    )
    ORDER BY class_name

-- risks - all
    WITH
    ABuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.1.0' AND agent_id = 'spring-realworld-backend'
    ),
    BBuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.2.0' AND agent_id = 'spring-realworld-backend'
    ),
    ABuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum
        FROM raw_data.ast_method am
        JOIN ABuildInstanceIds ON am.instance_id = ABuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    BBuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum
        FROM raw_data.ast_method am
        JOIN BBuildInstanceIds ON am.instance_id = BBuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    )
    SELECT *
    FROM BBuildMethods AS q2
    WHERE
        -- modifed
        EXISTS (
            SELECT 1
            FROM ABuildMethods AS q1
            WHERE q1.signature = q2.signature
            AND q1.body_checksum <> q2.body_checksum
        )
        -- new
        OR NOT EXISTS (
            SELECT 1
            FROM ABuildMethods AS q1
            WHERE q1.signature = q2.signature
        )
    ORDER BY class_name

-- risks coverage
    WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.2.0' AND agent_id = 'spring-realworld-backend'
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    CoverageData AS (
        SELECT
            ecd.class_name,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
        GROUP BY ecd.class_name
    ),
    Risks AS (
        WITH
        ParentBuildInstanceIds AS (
            SELECT DISTINCT instance_id
            FROM raw_data.agent_config
            WHERE build_version = '0.1.0' AND agent_id = 'spring-realworld-backend'
        ),
        ParentBuildMethods AS (
            SELECT DISTINCT
                CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
                am.class_name,
                am.name,
                am.probe_start_pos,
                am.probes_count,
                am.body_checksum
            FROM raw_data.ast_method am
            JOIN ParentBuildInstanceIds ON am.instance_id = ParentBuildInstanceIds.instance_id
            WHERE am.probes_count > 0
        )
        SELECT *
        FROM BuildMethods AS q2
        WHERE
            -- modifed
            EXISTS (
                SELECT 1
                FROM ParentBuildMethods AS q1
                WHERE q1.signature = q2.signature
                AND q1.body_checksum <> q2.body_checksum
            )
            -- new
            OR NOT EXISTS (
                SELECT 1
                FROM ParentBuildMethods AS q1
                WHERE q1.signature = q2.signature
            )
    )
    SELECT
        rsk.class_name,
        rsk.name,
        COALESCE(
            (BIT_COUNT(SUBSTRING(cd.or_result FROM rsk.probe_start_pos + 1 FOR rsk.probes_count)) * 100.0 / rsk.probes_count)
            , 0) AS set_bits_ratio
    FROM Risks rsk
    LEFT JOIN CoverageData cd ON cd.class_name = rsk.class_name
    ORDER BY set_bits_ratio, class_name

-- risks coverage - detailization by test metadata
	WITH
    BuildInstanceIds AS (
        SELECT DISTINCT instance_id
        FROM raw_data.agent_config
        WHERE build_version = '0.2.0' AND agent_id = 'spring-realworld-backend'
    ),
    BuildMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum
        FROM raw_data.ast_method am
        JOIN BuildInstanceIds ON am.instance_id = BuildInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    ClassCoverage AS (
        SELECT
            ecd.class_name,
			ecd.test_id,
			BIT_OR(ecd.probes) AS or_result
        FROM raw_data.exec_class_data ecd
        JOIN BuildInstanceIds ON ecd.instance_id = BuildInstanceIds.instance_id
		GROUP BY ecd.class_name, ecd.test_id
    ),
    Risks AS (
        WITH
        ParentBuildInstanceIds AS (
            SELECT DISTINCT instance_id
            FROM raw_data.agent_config
            WHERE build_version = '0.1.0' AND agent_id = 'spring-realworld-backend'
        ),
        ParentBuildMethods AS (
            SELECT DISTINCT
                CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
                am.class_name,
                am.name,
                am.probe_start_pos,
                am.probes_count,
                am.body_checksum
            FROM raw_data.ast_method am
            JOIN ParentBuildInstanceIds ON am.instance_id = ParentBuildInstanceIds.instance_id
            WHERE am.probes_count > 0
        )
        SELECT *
        FROM BuildMethods AS q2
        WHERE
            -- modifed
            EXISTS (
                SELECT 1
                FROM ParentBuildMethods AS q1
                WHERE q1.signature = q2.signature
                AND q1.body_checksum <> q2.body_checksum
            )
            -- new
            OR NOT EXISTS (
                SELECT 1
                FROM ParentBuildMethods AS q1
                WHERE q1.signature = q2.signature
            )
    ),
	CoverageByRisk AS (
		SELECT
			rsk.*,
			cc.test_id,
	  		SUBSTRING(cc.or_result FROM rsk.probe_start_pos + 1 FOR rsk.probes_count) as method_or_result,
			cc.or_result
		FROM Risks rsk
		LEFT JOIN ClassCoverage cc ON cc.class_name = rsk.class_name
	),
	Res AS (
		SELECT
			CoverageByRisk.signature,
			CoverageByRisk.test_id,
			tm.name as test_name,
			tm.type as test_type,
			COALESCE(
				BIT_COUNT(BIT_OR(CoverageByRisk.method_or_result))
				* 100.0
				/ LENGTH(BIT_OR(CoverageByRisk.method_or_result)),
				0) as set_bits_ratio
		FROM CoverageByRisk
		LEFT JOIN raw_data.test_metadata tm ON tm.test_id = CoverageByRisk.test_id
		GROUP BY
			CoverageByRisk.signature,
			CoverageByRisk.test_id,
			test_name,
			test_type
		ORDER BY
			CoverageByRisk.signature
	)
	SELECT *
	FROM Res
	WHERE set_bits_ratio > 0
	ORDER BY signature

-- risks coverage - with intermediate builds coverage taken into account
    -- it relies on ASCII order to get intermediate builds, see query comments
    WITH
    BaselineInstanceIds AS (
        SELECT DISTINCT instance_id, build_version
        FROM raw_data.agent_config
        WHERE build_version = '0.1.0' AND agent_id = 'spring-realworld-backend'
    ),
    BaselineMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum,
            BaselineInstanceIds.build_version
        FROM raw_data.ast_method am
        JOIN BaselineInstanceIds ON am.instance_id = BaselineInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    ChildrenInstanceIds AS (
        SELECT DISTINCT instance_id, build_version
        FROM raw_data.agent_config
        WHERE
            agent_id = 'spring-realworld-backend'
                                         -- replace this with JOIN on table containing intermediate builds info
            AND build_version <= '0.3.0' -- 0.3.0 is the subject build; all builds "less" than that are intermediate ones
            AND build_version <> '0.1.0' -- 0.1.0 is the selected baseline build so its excluded
        ORDER BY build_version desc
    ),
    ChildrenMethods AS (
        SELECT DISTINCT
            CONCAT(am.class_name, ', ', am.name, ', ', am.params, ', ', am.return_type) AS signature,
            am.class_name,
            am.name,
            am.probe_start_pos,
            am.probes_count,
            am.body_checksum,
            ChildrenInstanceIds.build_version
        FROM raw_data.ast_method am
        JOIN ChildrenInstanceIds ON am.instance_id = ChildrenInstanceIds.instance_id
        WHERE am.probes_count > 0
    ),
    ChildrenRisks AS (
        SELECT
            build_version,
            name,
            class_name,
            body_checksum,
            signature,
            probe_start_pos,
            probes_count
        FROM ChildrenMethods AS q2
        WHERE
        -- modifed
        EXISTS (
            SELECT 1
            FROM BaselineMethods AS q1
            WHERE q1.signature = q2.signature
            AND q1.body_checksum <> q2.body_checksum
        )
        -- new
        OR NOT EXISTS (
            SELECT 1
            FROM BaselineMethods AS q1
            WHERE q1.signature = q2.signature
        )
        ORDER BY build_version desc, signature
    ),
    LatestRisks AS (
        SELECT *
        FROM ChildrenRisks
        WHERE build_version = '0.3.0'
    ),
    IntermediateRisks AS (
        SELECT *
        FROM ChildrenRisks
        WHERE build_version < '0.3.0'
    ),
    PreviousRisks AS (
        SELECT
            LatestRisks.class_name,
            LatestRisks.name,
            LatestRisks.signature,
            LatestRisks.body_checksum,
            LatestRisks.probe_start_pos,
            LatestRisks.probes_count
        FROM IntermediateRisks
        JOIN LatestRisks ON
            LatestRisks.signature = IntermediateRisks.signature
            AND LatestRisks.body_checksum = IntermediateRisks.body_checksum
    ),
    ChildrenCoverage AS (
        SELECT
            ecd.class_name,
            build_version,
            BIT_OR(ecd.probes) AS or_result
        FROM raw_data.exec_class_data ecd
        JOIN ChildrenInstanceIds ON ecd.instance_id = ChildrenInstanceIds.instance_id
        GROUP BY build_version, ecd.class_name
    ),
    ChildrenRiskCoverage AS (
        SELECT
            rsk.class_name,
            rsk.name,
            cd.build_version as covered_in_version,
            COALESCE(
                (BIT_COUNT(SUBSTRING(cd.or_result FROM rsk.probe_start_pos + 1 FOR rsk.probes_count)) * 100.0 / rsk.probes_count)
                , 0) AS set_bits_ratio
        FROM PreviousRisks rsk
        LEFT JOIN ChildrenCoverage cd ON
            rsk.class_name = cd.class_name
            AND BIT_COUNT(SUBSTRING(cd.or_result FROM rsk.probe_start_pos + 1 FOR rsk.probes_count)) > 0
    )
    SELECT *
    FROM ChildrenRiskCoverage
    ORDER BY covered_in_version DESC


*/

/*

-- all risks "cleaner" version (TODO check if it works correctly)
SELECT q2.*
FROM raw_data.ast_method AS q2
LEFT JOIN raw_data.ast_method AS q1
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

* */
