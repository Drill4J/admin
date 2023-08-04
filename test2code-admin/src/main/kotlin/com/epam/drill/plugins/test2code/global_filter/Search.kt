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
package com.epam.drill.plugins.test2code.global_filter

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.BetweenOp.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.logger
import com.epam.drill.plugins.test2code.storage.StoredSession
import com.epam.dsm.*
import com.epam.dsm.find.*
import com.epam.dsm.serializer.*
import com.epam.dsm.util.*

private val logger = logger {}

/**
 * @return List<testId> by filters
 */
suspend fun findTestsByFilters(
    storeClient: StoreClient,
    sessionsIds: List<String>,
    testOverviewFilters: List<TestOverviewFilter>,
): List<String> {
    val filters = toExprFilters(testOverviewFilters, sessionsIds)
    val testIds = storeClient.findBy(filters).getAndMap(TestOverview::testId)
    logger.debug { "With filters '$testOverviewFilters' found tests: $testIds" }
    return testIds
}

private fun toExprFilters(
    inputFilters: List<TestOverviewFilter>,
    sessionsIds: List<String>,
): Expr<TestOverview>.() -> Unit = {
    inputFilters.fold(containsParentId(sessionsIds)) { exprFilters: Expr<TestOverview>, filter: TestOverviewFilter ->
        val values = filter.values.map { it.value }
        if (filter.isLabel) {
            val fieldPath = FieldPath(TestOverview::details.name, TestDetails::labels.name)
            when (filter.valuesOp) {
                OR -> {
                    val containValues = fieldPath.anyInCollection<Label> {
                        (Label::name eqIgnoreCase filter.fieldPath) and (Label::value contains values)
                    }
                    exprFilters and containValues
                }
                AND -> {
                    val eqValues = values.fold(exprFilters) { acc, value ->
                        acc and (fieldPath.anyInCollection<Label> {
                            (Label::name eqIgnoreCase filter.fieldPath) and (Label::value eqIgnoreCase value)
                        })
                    }
                    eqValues
                }
            }
        } else {
            val fieldPath = FieldPath(filter.fieldPath.split(PATH_DELIMITER))
            when (filter.valuesOp) {
                OR -> {
                    val containValues = fieldPath contains values
                    exprFilters and containValues
                }
                AND -> {
                    val eqValues = values.fold(exprFilters) { acc, value ->
                        acc and (fieldPath eq value)
                    }
                    eqValues
                }
            }
        }
    }
}

/**
 * @return Map<TestKey, TestOverview> by testIds
 */
fun findByTestType(
    storeClient: StoreClient,
    sessionsIds: List<String>,
    testIds: List<String>,
): Map<TestKey, TestOverview> {
    val result = storeClient.executeInTransaction {
        val sessionTable = StoredSession::class.tableName()
        val testOverviewTable = TestOverview::class.tableName()

        val result = mutableMapOf<TestKey, TestOverview>()
        val tempTable = "overview"
        val sql = """
            with $tempTable as (SELECT *
                         from $testOverviewTable
                         where $PARENT_ID_COLUMN ${sessionsIds.toSqlIn()}
                           and ${FieldPath(TestOverview::testId).extractText()} ${testIds.toSqlIn()})
            select $sessionTable.${FieldPath(StoredSession::data, FinishedSession::testType).extractText()},
                   $tempTable.$JSON_COLUMN
            from $sessionTable,
                 $tempTable
            where $sessionTable.$ID_COLUMN in ($tempTable.$PARENT_ID_COLUMN)
        """.trimIndent()
        logger.debug { "sql for testOverview:\n$sql" }
        connection.prepareStatement(sql, false).executeQuery().let {
            while (it.next()) {
                val testType: String = it.getString(1)
                val testOverview: TestOverview = dsmDecode(it.getBinaryStream(2), TestOverview::class.java.classLoader)
                result[TestKey(testOverview.testId, testType)] = testOverview
            }
        }
        result
    }
    logger.debug { "testType and testOverview $result" }
    return result
}

suspend fun findProbes(
    storeClient: StoreClient,
    sessionsIds: List<String>,
    testIds: List<String>,
): List<ExecClassData> = storeClient.findBy<ExecClassData> {
    containsParentId(sessionsIds) and ExecClassData::testId.contains(testIds)
}.get()

/**
 * @return Map<testId, Sequence<Probes>
 */
fun groupProbes(
    storeClient: StoreClient,
    sessionsIds: List<String>,
    testIds: List<String>,
): Map<String, Sequence<ExecClassData>> {
    val probesByTestId = storeClient.executeInTransaction {
        val probesTable = ExecClassData::class.tableName()
        val testIdPath = FieldPath(ExecClassData::testId).extractText()
        val sql = """
                SELECT $testIdPath,
                       json_agg(${fullJson<ExecClassData>()})
                from $probesTable
                where $PARENT_ID_COLUMN ${sessionsIds.toSqlIn()}
                  and $testIdPath ${testIds.toSqlIn()}
                group by $testIdPath, $JSON_COLUMN #>> '{${ExecClassData::testId.name}}'  
            """.trimIndent()
        logger.debug { "sql for probes:\n$sql" }
        val result = mutableMapOf<String, Sequence<ExecClassData>>()
        connection.prepareStatement(sql, false).executeQuery().let { rs ->
            while (rs.next()) {
                val testId: String = rs.getString(1)
                val probes: List<ExecClassData> = dsmDecode(rs.getBinaryStream(2), classLoader<ExecClassData>())
                result[testId] = probes.asSequence()
            }
        }
        result
    }
    logger.trace { "Probes grouped by testId: $probesByTestId" }
    return probesByTestId
}


