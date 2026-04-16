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
import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import com.epam.drill.admin.writer.rawdata.table.*
import io.ktor.client.request.*
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertTrue

class CoverageTreemapTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    @Test
    fun `given build with no methods no coverage, coverage-treemap should return empty list`() = havingData {
        client.putBuild(BuildPayload(groupId = testGroup, appId = testApp, buildVersion = "1.0.0", branch = "main"))
    }.expectThat {
        client.get("/metrics/coverage-treemap") {
            parameter("buildId", "${testGroup}:${testApp}:1.0.0")
        }.returns { data ->
            assertTrue(data.isEmpty())
        }
    }

    @Test
    fun `given build with methods but no coverage, coverage-treemap should return non-empty list with zero coverage`() = havingData {
        build1 has listOf(method1, method2)
    }.expectThat {
        client.get("/metrics/coverage-treemap") {
            parameter("buildId", "${build1.groupId}:${build1.appId}:${build1.buildVersion}")
        }.returns { data ->
            assertTrue(data.isNotEmpty())
            assertTrue(data.any { it["name"].toString().startsWith(method1.name) && it["covered_probes"] == 0 })
            assertTrue(data.any { it["name"].toString().startsWith(method2.name) && it["covered_probes"] == 0 })
        }
    }

    @Test
    fun `given build with methods and coverage, coverage-treemap should return non-empty list`() = havingData {
        build1 has listOf(method1, method2)
        test1 covers method1 with probesOf(1, 1) on build1
    }.expectThat {
        client.get("/metrics/coverage-treemap") {
            parameter("buildId", "${build1.groupId}:${build1.appId}:${build1.buildVersion}")
        }.returns { data ->
            assertTrue(data.isNotEmpty())
            assertTrue(data.any { it["name"].toString().startsWith(method1.name) && it["covered_probes"] == 2 })
            assertTrue(data.any { it["name"].toString().startsWith(method2.name) && it["covered_probes"] == 0 })
        }
    }

    @Test
    fun `given build with coverage from multiple sessions, coverage-treemap filtered by testSessionId should return only that session coverage`() = havingData {
        build1 has listOf(method1, method2)
        test1 of session1 covers method1 with probesOf(1, 1) on build1
        test2 of session2 covers method2 with probesOf(1, 1, 1) on build1
    }.expectThat {
        // Without filter - all coverage
        client.get("/metrics/coverage-treemap") {
            parameter("buildId", "${build1.groupId}:${build1.appId}:${build1.buildVersion}")
        }.returns { data ->
            assertTrue(data.isNotEmpty())
            assertTrue(data.any { it["name"].toString().startsWith(method1.name) && it["covered_probes"] == 2 })
            assertTrue(data.any { it["name"].toString().startsWith(method2.name) && it["covered_probes"] == 3 })
        }
        // Filter by session1 - only method1 coverage
        client.get("/metrics/coverage-treemap") {
            parameter("buildId", "${build1.groupId}:${build1.appId}:${build1.buildVersion}")
            parameter("testSessionId", session1.id)
        }.returns { data ->
            assertTrue(data.isNotEmpty())
            assertTrue(data.any { it["name"].toString().startsWith(method1.name) && it["covered_probes"] == 2 })
            assertTrue(data.any { it["name"].toString().startsWith(method2.name) && it["covered_probes"] == 0 })
        }
        // Filter by session2 - only method2 coverage
        client.get("/metrics/coverage-treemap") {
            parameter("buildId", "${build1.groupId}:${build1.appId}:${build1.buildVersion}")
            parameter("testSessionId", session2.id)
        }.returns { data ->
            assertTrue(data.isNotEmpty())
            assertTrue(data.any { it["name"].toString().startsWith(method1.name) && it["covered_probes"] == 0 })
            assertTrue(data.any { it["name"].toString().startsWith(method2.name) && it["covered_probes"] == 3 })
        }
    }

    @Test
    fun `given build with coverage, coverage-treemap filtered by testDefinitionId should return only that test definition coverage`() {
        havingData {
            build1 has listOf(method1, method2)
            test1 of session1 covers method1 with probesOf(1, 1) on build1
            test2 of session1 covers method2 with probesOf(1, 1, 1) on build1
            test2 of session2 covers method2 with probesOf(1, 0, 0) on build1
        }.expectThat {
            // Filter by test1 definition (test1 covering method1) - requires testSessionId
            client.get("/metrics/coverage-treemap") {
                parameter("buildId", "${build1.groupId}:${build1.appId}:${build1.buildVersion}")
                parameter("testSessionId", session1.id)
                parameter("testDefinitionId", test1.definitionId)
            }.returns { data ->
                assertTrue(data.isNotEmpty())
                assertTrue(data.any { it["name"].toString().startsWith(method1.name) && it["covered_probes"] == 2 })
                assertTrue(data.any { it["name"].toString().startsWith(method2.name) && it["covered_probes"] == 0 })
            }
            // Filter by test2 definition (test2 covering method2) - requires testSessionId
            client.get("/metrics/coverage-treemap") {
                parameter("buildId", "${build1.groupId}:${build1.appId}:${build1.buildVersion}")
                parameter("testSessionId", session1.id)
                parameter("testDefinitionId", test2.definitionId)
            }.returns { data ->
                assertTrue(data.isNotEmpty())
                assertTrue(data.any { it["name"].toString().startsWith(method1.name) && it["covered_probes"] == 0 })
                assertTrue(data.any { it["name"].toString().startsWith(method2.name) && it["covered_probes"] == 3 })
            }
        }
    }

    @AfterEach
    fun clearAll() = withTransaction {
        MethodCoverageTable.deleteAll()
        InstanceTable.deleteAll()
        MethodTable.deleteAll()
        BuildMethodTable.deleteAll()
        BuildTable.deleteAll()
        TestLaunchTable.deleteAll()
        TestSessionTable.deleteAll()
        TestDefinitionTable.deleteAll()
    }
}
