package com.epam.drill.plugins.test2code.multibranch.service

import com.epam.drill.plugins.test2code.multibranch.repository.RawDataRepositoryImpl
import com.epam.drill.plugins.test2code.multibranch.rawdata.config.DatabaseConfig
import kotlinx.serialization.json.*

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

    DatabaseConfig.getDataSource()?.connection.use { connection ->
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
            auth.exec_class_data
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
                auth.exec_class_data
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
                auth.exec_class_data
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
        FROM auth.ast_method AS q2
        WHERE instance_id = '$newInstanceId'
        AND NOT EXISTS (
            SELECT 1
            FROM auth.ast_method AS q1
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
    """.trimIndent()
}


/*

-- all risks "cleaner" version (TODO check if it works correctly)
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

* */
