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

class BuildDetailApiTest : MetricsDatabaseTests({ default, metrics ->
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
    fun `get build by id should return build details with statistics`() = havingData {
        build1 has listOf(method1, method2)
    }.expectThat {
        client.get("/metrics/builds/$build1Id").apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<Map<String, Any>>("$.data")
            assertEquals(testGroup, data["groupId"])
            assertEquals(testApp, data["appId"])
            assertEquals(build1Id, data["buildId"])
            assertEquals(2, data["totalMethods"])
        }
    }

    @Test
    fun `get changes summary should return change counts vs baseline`() = havingData {
        build1 has listOf(method1, method2)
        build2 hasModified method2 comparedTo build1
        build2 hasNew method3 comparedTo build1
    }.expectThat {
        client.get("/metrics/builds/$build2Id/changes-summary") {
            parameter("baselineBuildId", build1Id)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<Map<String, Any>>("$.data")
            assertEquals(1, data["newMethods"])
            assertEquals(1, data["modifiedMethods"])
        }
    }

    @Test
    fun `get similar builds should return baseline candidates`() = havingData {
        build1 has listOf(method1, method2)
        build2 hasModified method2 comparedTo build1
    }.expectThat {
        client.get("/metrics/builds/$build2Id/similar-builds").apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<List<Map<String, Any>>>("$.data")
            assertTrue(data.isNotEmpty())
            assertTrue(data.any { it["buildId"] == build1Id })
        }
    }

    @Test
    fun `get coverage by probes should return covered in other builds slice`() = havingData {
        build1 has listOf(method1, method2, method4)
        build2 hasModified method2 comparedTo build1
        build3 hasDeleted method4 comparedTo build2
        build3 hasNew method3 comparedTo build2
        test1 covers method2 with probesOf(1, 1, 0) on build2
        test2 covers method2 with probesOf(0, 0, 1) on build3
    }.expectThat {
        val build3Id = generateBuildId(
            testGroup, testApp, build3.instanceId, null, build3.buildVersion
        )
        client.get("/metrics/builds/$build3Id/coverage-by-probes").apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val slices = json.read<List<Map<String, Any>>>("$.data.slices")
            val sliceByMetric = slices.associate { it["metric"] as String to (it["value"] as Int) }
            assertTrue((sliceByMetric["covered_in_other_builds"] ?: 0) > 0)
            assertEquals(
                sliceByMetric.values.sum(),
                (sliceByMetric["covered"] ?: 0) +
                    (sliceByMetric["covered_in_other_builds"] ?: 0) +
                    (sliceByMetric["gaps"] ?: 0),
            )
        }
    }

    @AfterEach
    fun clearAll() = withTransaction(RawDataWriterDatabaseConfig.database) {
        BuildTable.deleteAll()
    }
}
