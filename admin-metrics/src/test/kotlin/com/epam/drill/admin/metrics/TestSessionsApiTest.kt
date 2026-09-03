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
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSessionsApiTest : MetricsDatabaseTests({ default, metrics ->
    RawDataWriterDatabaseConfig.init(default)
    MetricsDatabaseConfig.init(metrics)
}) {
    private val build1Id = "${build1.groupId}:${build1.appId}:${build1.buildVersion}"

    @Test
    fun `given build with test sessions, should return sessions for that build`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session2 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/builds/$build1Id/test-sessions") {
                parameter("groupId", testGroup)
            }.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.all { it["buildId"] == build1Id })
                assertTrue(data.any { it["testSessionId"] == session1.id })
                assertTrue(data.any { it["testSessionId"] == session2.id })
            }
        }

    @Test
    fun `given test sessions on different builds, group list returns all sessions`(): Unit =
        havingData {
            build1 has listOf(method1)
            build2 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test1 of session2 covers method1 with probesOf(1, 1) on build2
        }.expectThat {
            client.get("/metrics/test-sessions") {
                parameter("groupId", testGroup)
            }.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.any { it["testSessionId"] == session1.id })
                assertTrue(data.any { it["testSessionId"] == session2.id })
            }
        }

    @Test
    fun `given test sessions on different builds, build endpoint returns only matching sessions`(): Unit =
        havingData {
            build1 has listOf(method1)
            build2 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test1 of session2 covers method1 with probesOf(1, 1) on build2
        }.expectThat {
            client.get("/metrics/builds/$build1Id/test-sessions") {
                parameter("groupId", testGroup)
            }.returns { data ->
                assertEquals(1, data.size)
                assertEquals(session1.id, data[0]["testSessionId"])
            }
        }

    @Test
    fun `given page and pageSize, should return paginated sessions with total`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session2 covers method1 with probesOf(1, 1) on build1
            test3 of session3 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            val response = client.get("/metrics/builds/$build1Id/test-sessions") {
                parameter("groupId", testGroup)
                parameter("page", 1)
                parameter("pageSize", 2)
            }
            response.returns { data ->
                assertEquals(2, data.size)
            }
            val total = JsonPath.read<Number>(response.bodyAsText(), "$.paging.total")
            assertEquals(3, total.toInt())
        }

    @Test
    fun `given sortBy successRate DESC, sessions should be ordered by success rate`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session2 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/builds/$build1Id/test-sessions") {
                parameter("groupId", testGroup)
                parameter("sortBy", "successRate")
                parameter("sortOrder", "DESC")
            }.returns { data ->
                assertTrue(data.size >= 2)
            }
        }

    @Test
    fun `given invalid sortBy, should return BadRequest`(): Unit =
        havingData {
            build1 has listOf(method1)
        }.expectThat {
            val response = client.get("/metrics/builds/$build1Id/test-sessions") {
                parameter("groupId", testGroup)
                parameter("sortBy", "invalidSortBy")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Invalid sortBy"))
        }

    @Test
    fun `given filter options endpoint, should return distinct values for build`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/filter-options") {
                parameter("groupId", testGroup)
                parameter("buildId", build1Id)
                parameter("field", "testTaskIds")
            }.returnsStrings { data ->
                assertTrue(data.contains(testTask))
            }

            client.get("/metrics/test-sessions/filter-options") {
                parameter("groupId", testGroup)
                parameter("buildId", build1Id)
                parameter("field", "results")
            }.returnsStrings { data ->
                assertTrue(data.contains("PASSED"))
            }
        }

    @Test
    fun `given filter options without buildId, should return distinct values for group`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions/filter-options") {
                parameter("groupId", testGroup)
                parameter("field", "testTaskIds")
            }.returnsStrings { data ->
                assertTrue(data.contains(testTask))
            }

            client.get("/metrics/test-sessions/filter-options") {
                parameter("groupId", testGroup)
                parameter("field", "results")
            }.returnsStrings { data ->
                assertTrue(data.contains("PASSED"))
            }

            val invalid = client.get("/metrics/test-sessions/filter-options") {
                parameter("groupId", testGroup)
                parameter("field", "invalidField")
            }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
        }

    @Test
    fun `given same session on multiple builds, group list returns one row per session`(): Unit =
        havingData {
            build1 has listOf(method1)
            build2 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test1 of session1 covers method1 with probesOf(1, 1) on build2
        }.expectThat {
            client.get("/metrics/test-sessions") {
                parameter("groupId", testGroup)
            }.returns { data ->
                assertEquals(1, data.size)
                assertEquals(session1.id, data[0]["testSessionId"])
            }
        }

    @Test
    fun `given session with builds, builds endpoint returns linked builds`(): Unit =
        havingData {
            build1 has listOf(method1)
            build2 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test1 of session1 covers method1 with probesOf(1, 1) on build2
        }.expectThat {
            val response = client.get("/metrics/test-sessions/${session1.id}/builds") {
                parameter("groupId", testGroup)
            }
            response.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.any { it["buildId"] == build1Id })
                assertTrue(data.any { it["buildId"] == "${build2.groupId}:${build2.appId}:${build2.buildVersion}" })
            }
            val total = JsonPath.read<Number>(response.bodyAsText(), "$.paging.total")
            assertEquals(2, total.toInt())
        }

    @Test
    fun `given session with builds, builds endpoint respects pageSize`(): Unit =
        havingData {
            build1 has listOf(method1)
            build2 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test1 of session1 covers method1 with probesOf(1, 1) on build2
        }.expectThat {
            val response = client.get("/metrics/test-sessions/${session1.id}/builds") {
                parameter("groupId", testGroup)
                parameter("page", 1)
                parameter("pageSize", 1)
            }
            response.returns { data ->
                assertEquals(1, data.size)
            }
            val total = JsonPath.read<Number>(response.bodyAsText(), "$.paging.total")
            assertEquals(2, total.toInt())
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
