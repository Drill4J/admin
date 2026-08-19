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

import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig
import com.epam.drill.admin.test.MetricsDatabaseTests
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.table.BuildMethodTable
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodCoverageTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import com.jayway.jsonpath.JsonPath
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSessionDetailApiTest : MetricsDatabaseTests({ default, metrics ->
    RawDataWriterDatabaseConfig.init(default)
    MetricsDatabaseConfig.init(metrics)
}) {
    private val build1Id = "${build1.groupId}:${build1.appId}:${build1.buildVersion}"

    @Test
    fun `given test session, should return session detail`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/${session1.id}") {
                parameter("groupId", testGroup)
            }.returnsSingle { data ->
                assertEquals(session1.id, data["testSessionId"])
                assertEquals(testGroup, data["groupId"])
                assertEquals(build1Id, data["buildId"])
                assertEquals(2, (data["testDefinitions"] as Number).toInt())
            }
        }

    @Test
    fun `given wrong groupId, should return NotFound`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            val response = client.get("/metrics/test-sessions/${session1.id}") {
                parameter("groupId", "other-group")
            }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `given session with partial coverage, coverage summary totals should match build`(): Unit =
        havingData {
            build1 has listOf(method1, method2)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            val probesResponse = client.get("/metrics/builds/$build1Id/coverage-by-probes")
            val buildProbes = JsonPath.read<List<Map<String, Any>>>(
                probesResponse.bodyAsText(),
                "$.data.slices"
            ).sumOf { (it["value"] as Number).toInt() }

            val methodsResponse = client.get("/metrics/builds/$build1Id/coverage-by-methods")
            val buildMethods = JsonPath.read<List<Map<String, Any>>>(
                methodsResponse.bodyAsText(),
                "$.data.slices"
            ).sumOf { (it["value"] as Number).toInt() }

            client.get("/metrics/test-sessions/${session1.id}/coverage-summary") {
                parameter("groupId", testGroup)
                parameter("buildId", build1Id)
            }.returnsSingle { data ->
                @Suppress("UNCHECKED_CAST")
                val probesSlices = (data["probes"] as Map<String, Any>)["slices"] as List<Map<String, Any>>
                @Suppress("UNCHECKED_CAST")
                val methodsSlices = (data["methods"] as Map<String, Any>)["slices"] as List<Map<String, Any>>
                val probesByMetric = probesSlices.associate { it["metric"] as String to (it["value"] as Number).toInt() }
                val methodsByMetric = methodsSlices.associate { it["metric"] as String to (it["value"] as Number).toInt() }

                assertEquals(buildProbes, probesSlices.sumOf { (it["value"] as Number).toInt() })
                assertEquals(buildMethods, methodsSlices.sumOf { (it["value"] as Number).toInt() })
                assertEquals(2, probesByMetric["covered"])
                assertEquals(1, methodsByMetric["covered"])
                assertEquals(buildProbes - 2, probesByMetric["missed"])
                assertEquals(1, methodsByMetric["missed"])
            }
        }

    @Test
    fun `given session with coverage, should return coverage summary`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/${session1.id}/coverage-summary") {
                parameter("groupId", testGroup)
                parameter("buildId", build1Id)
            }.returnsSingle { data ->
                @Suppress("UNCHECKED_CAST")
                val probesSlices = (data["probes"] as Map<String, Any>)["slices"] as List<Map<String, Any>>
                val probesByMetric = probesSlices.associate { it["metric"] as String to (it["value"] as Number).toInt() }

                assertTrue(probesSlices.sumOf { (it["value"] as Number).toInt() } > 0)
                assertTrue((probesByMetric["covered"] ?: 0) > 0)
            }
        }

    @Test
    fun `given session with tests, should return launches and file launches`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/${session1.id}/launches") {
                parameter("groupId", testGroup)
            }.returns { data ->
                assertEquals(2, data.size)
            }
            client.get("/metrics/test-sessions/${session1.id}/file-launches") {
                parameter("groupId", testGroup)
            }.returns { data ->
                assertTrue(data.isNotEmpty())
                assertEquals(testPath, data[0]["testPath"])
            }
        }

    @Test
    fun `coverage filtered by testSessionId and className should return class methods`(): Unit =
        havingData {
            build1 has listOf(method1, method2)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test1 of session1 covers method2 with probesOf(0, 0, 1) on build1
        }.expectThat {
            client.get("/metrics/coverage") {
                parameter("buildId", build1Id)
                parameter("testSessionId", session1.id)
                parameter("className", method1.classname)
            }.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.any { it["name"] == method1.name })
                assertTrue(data.any { it["name"] == method2.name })
            }
        }

    @Test
    fun `definitions endpoint should return paginated results and support query`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/${session1.id}/definitions") {
                parameter("groupId", testGroup)
                parameter("page", 1)
                parameter("pageSize", 1)
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                val total = json.read<Int>("$.paging.total")
                assertEquals(1, data.size)
                assertEquals(2, total)
            }

            client.get("/metrics/test-sessions/${session1.id}/definitions") {
                parameter("groupId", testGroup)
                parameter("query", test1.testName)
                parameter("page", 1)
                parameter("pageSize", 20)
            }.returns { data ->
                assertEquals(1, data.size)
                assertEquals(test1.testName, data[0]["testName"])
            }
        }

    @Test
    fun `file launches should sort by successRate and reject invalid sortBy`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/${session1.id}/file-launches") {
                parameter("groupId", testGroup)
                parameter("sortBy", "successRate")
                parameter("sortOrder", "DESC")
            }.returns { data ->
                assertTrue(data.isNotEmpty())
            }

            val invalid = client.get("/metrics/test-sessions/${session1.id}/file-launches") {
                parameter("groupId", testGroup)
                parameter("sortBy", "invalidSortBy")
            }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
            assertTrue(invalid.bodyAsText().contains("Invalid sortBy"))
        }

    @Test
    fun `file launches filter-options should return paths and results`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/${session1.id}/file-launches/filter-options") {
                parameter("groupId", testGroup)
            }.returnsSingle { data ->
                @Suppress("UNCHECKED_CAST")
                val testPaths = data["testPaths"] as List<String>
                @Suppress("UNCHECKED_CAST")
                val results = data["results"] as List<String>
                assertTrue(testPaths.contains(testPath))
                assertTrue(results.isNotEmpty())
            }

            client.get("/metrics/test-sessions/${session1.id}/file-launches/filter-options") {
                parameter("groupId", testGroup)
                parameter("page", 1)
            }.returnsStrings { data ->
                assertTrue(data.contains(testPath))
            }
        }

    @Test
    fun `launches should filter by testName and sort by testLaunches`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/${session1.id}/launches") {
                parameter("groupId", testGroup)
                parameter("testNames", test1.testName)
            }.returns { data ->
                assertEquals(1, data.size)
                assertEquals(test1.testName, data[0]["testName"])
            }

            client.get("/metrics/test-sessions/${session1.id}/launches") {
                parameter("groupId", testGroup)
                parameter("sortBy", "testLaunches")
                parameter("sortOrder", "DESC")
            }.returns { data ->
                assertEquals(2, data.size)
            }

            val invalid = client.get("/metrics/test-sessions/${session1.id}/launches") {
                parameter("groupId", testGroup)
                parameter("sortBy", "invalidSortBy")
            }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
            assertTrue(invalid.bodyAsText().contains("Invalid sortBy"))
        }

    @Test
    fun `file launches page should return the page for a path`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/${session1.id}/file-launches/page") {
                parameter("groupId", testGroup)
                parameter("path", testPath)
                parameter("pageSize", 1)
            }.returnsSingle { data ->
                assertEquals(1, (data["page"] as Number).toInt())
            }

            val invalid = client.get("/metrics/test-sessions/${session1.id}/file-launches/page") {
                parameter("groupId", testGroup)
                parameter("path", testPath)
                parameter("sortBy", "invalidSortBy")
            }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)

            val missing = client.get("/metrics/test-sessions/${session1.id}/file-launches/page") {
                parameter("groupId", testGroup)
                parameter("path", "missing.spec.ts")
            }
            assertEquals(HttpStatusCode.NotFound, missing.status)
        }

    @Test
    fun `launches page should return the page for a launchId`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/${session1.id}/launches/page") {
                parameter("groupId", testGroup)
                parameter("path", testPath)
                parameter("launchId", test2.definitionId)
                parameter("pageSize", 1)
            }.returnsSingle { data ->
                assertEquals(2, (data["page"] as Number).toInt())
            }

            val invalid = client.get("/metrics/test-sessions/${session1.id}/launches/page") {
                parameter("groupId", testGroup)
                parameter("launchId", test1.definitionId)
                parameter("sortBy", "invalidSortBy")
            }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)

            val missing = client.get("/metrics/test-sessions/${session1.id}/launches/page") {
                parameter("groupId", testGroup)
                parameter("launchId", "missing-launch")
            }
            assertEquals(HttpStatusCode.NotFound, missing.status)
        }

    @AfterEach
    fun cleanup() = withTransaction(RawDataWriterDatabaseConfig.database) {
        MethodCoverageTable.deleteAll()
        TestLaunchTable.deleteAll()
        TestDefinitionTable.deleteAll()
        TestSessionTable.deleteAll()
        BuildMethodTable.deleteAll()
        MethodTable.deleteAll()
        BuildTable.deleteAll()
        InstanceTable.deleteAll()
    }
}
