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
    fun `given build with test sessions, should return sessions filtered by buildId`(): Unit =
        havingData {
            build1 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session2 covers method1 with probesOf(1, 1) on build1
        }.expectThat {
            client.get("/metrics/test-sessions") {
                parameter("groupId", testGroup)
                parameter("buildId", build1Id)
            }.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.all { it["buildId"] == build1Id })
                assertTrue(data.any { it["testSessionId"] == session1.id })
                assertTrue(data.any { it["testSessionId"] == session2.id })
            }
        }

    @Test
    fun `given test sessions on different builds, buildId filter should return only matching sessions`(): Unit =
        havingData {
            build1 has listOf(method1)
            build2 has listOf(method1)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test1 of session2 covers method1 with probesOf(1, 1) on build2
        }.expectThat {
            client.get("/metrics/test-sessions") {
                parameter("groupId", testGroup)
                parameter("buildId", build1Id)
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
            val response = client.get("/metrics/test-sessions") {
                parameter("groupId", testGroup)
                parameter("buildId", build1Id)
                parameter("page", 1)
                parameter("pageSize", 2)
            }
            response.returns { data ->
                assertEquals(2, data.size)
            }
            val total = JsonPath.read<Number>(response.bodyAsText(), "$.paging.total")
            assertEquals(3, total.toInt())
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
