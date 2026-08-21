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
package com.epam.drill.admin.metrics

import com.epam.drill.admin.common.service.generateBuildId
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.test.MetricsDatabaseTests
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.jayway.jsonpath.JsonPath
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppTrendsApiTest : MetricsDatabaseTests({ default, metrics ->
    RawDataWriterDatabaseConfig.init(default)
    MetricsDatabaseConfig.init(metrics)
}) {
    private val build1Id = generateBuildId(
        testGroup, testApp, build1.instanceId, null, build1.buildVersion
    )
    private val build2Id = generateBuildId(
        testGroup, testApp, build2.instanceId, null, build2.buildVersion
    )

    @Test
    fun `get coverage trends works without baseline across recent builds`() = havingData {
        build1 has listOf(method1, method2)
        build2 hasModified method2 comparedTo build1
        test1 covers method1 with probesOf(1, 1) on build1
        test1 covers method2 with probesOf(1, 0, 1) on build2
    }.expectThat {
        client.get("/metrics/apps/trends/coverage") {
            parameter("groupId", testGroup)
            parameter("appId", testApp)
            parameter("size", 10)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<List<Map<String, Any>>>("$.data")
            assertTrue(data.size >= 2)
            assertTrue(data.any { it["buildId"] == build1Id })
            assertTrue(data.any { it["buildId"] == build2Id })
            data.forEach { point ->
                val isolated = (point["isolatedCoveragePercent"] as Number).toDouble()
                val other = (point["otherBuildsCoveragePercent"] as Number).toDouble()
                val aggregated = (point["aggregatedCoveragePercent"] as Number).toDouble()
                assertTrue(aggregated + 0.0001 >= isolated)
                assertEquals(aggregated, isolated + other, absoluteTolerance = 0.0001)
            }
        }
    }

    @Test
    fun `get changes trends requires baseline and returns probes and methods`() = havingData {
        build1 has listOf(method1, method2)
        build2 hasModified method2 comparedTo build1
        test1 covers method2 with probesOf(1, 0, 1) on build2
    }.expectThat {
        client.get("/metrics/apps/trends/changes") {
            parameter("groupId", testGroup)
            parameter("appId", testApp)
            parameter("size", 10)
        }.apply {
            assertTrue(status == HttpStatusCode.BadRequest || status == HttpStatusCode.InternalServerError)
        }

        client.get("/metrics/apps/trends/changes") {
            parameter("groupId", testGroup)
            parameter("appId", testApp)
            parameter("baselineBuildId", build1Id)
            parameter("size", 10)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<List<Map<String, Any>>>("$.data")
            assertTrue(data.size >= 2)
            assertEquals(build1Id, data[0]["buildId"])
            data.forEach { point ->
                val coveredProbes = (point["coveredProbes"] as Number).toInt()
                val aggregatedProbes = (point["coveredInOtherBuildsProbes"] as Number).toInt()
                val coveredMethods = (point["coveredMethods"] as Number).toInt()
                val aggregatedMethods = (point["coveredInOtherBuildsMethods"] as Number).toInt()
                assertTrue(aggregatedProbes >= coveredProbes)
                assertTrue(aggregatedMethods >= coveredMethods)
            }
        }
    }

    @AfterEach
    fun clearAll() = withTransaction(RawDataWriterDatabaseConfig.database) {
        BuildTable.deleteAll()
    }
}
