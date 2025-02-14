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
import com.epam.drill.admin.metrics.config.metricsDIModule
import com.epam.drill.admin.metrics.route.metricsRoutes
import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.drillApplication
import com.epam.drill.admin.test.drillClient
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import com.epam.drill.admin.writer.rawdata.route.dataIngestRoutes
import com.epam.drill.admin.writer.rawdata.table.*
import com.jayway.jsonpath.JsonPath
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecommendedTestsApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    @Test
    fun `given test that covers modified methods, recommended test service should suggest running it`() {
        runBlocking {
            val client = runDrillApplication().apply {
                deployInstance(build1, arrayOf(method1, method2))
                launchTest(test1, build1, arrayOf(method1 to probesOf(0, 0), method2 to probesOf(1, 0, 0)))
                deployInstance(build2, arrayOf(method1, method2.changeChecksum()))
            }

            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("targetBuildVersion", build2.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val recommendedTests = json.read<List<Map<String, Any>>>("$.data.recommendedTests")

                // test1 checked method2 in build1, but method2 was modified in build2,
                // that's why test1 should be recommended to run in build2
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
            }
        }
    }

    @Test
    fun `given test that covers unmodified methods, recommended test service should suggest skipping it`() {
        runBlocking {
            val client = runDrillApplication().apply {
                //build1
                deployInstance(build1, arrayOf(method1, method2))
                launchTest(test1, build1, arrayOf(method1 to probesOf(1, 0), method2 to probesOf(0, 0, 0)))
                //build2
                deployInstance(build2, arrayOf(method1, method2.changeChecksum()))
            }

            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("testsToSkip", true)
                parameter("targetBuildVersion", build2.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val recommendedTests = json.read<List<Map<String, Any>>>("$.data.recommendedTests")

                // test1 already checked method1 in build1 and method1 was not modified in build2,
                // that's why test1 should be recommended to skip in build2
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
            }
        }
    }

    @Test
    fun `given test that covers modified and unmodified methods, recommended test service should suggest running it`() {
        runBlocking {
            val client = runDrillApplication().apply {
                //build1
                deployInstance(build1, arrayOf(method1, method2))
                launchTest(test1, build1, arrayOf(method1 to probesOf(1, 0), method2 to probesOf(1, 0, 0)))
                //build2
                deployInstance(build2, arrayOf(method1, method2.changeChecksum()))
            }

            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("targetBuildVersion", build2.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val recommendedTests = json.read<List<Map<String, Any>>>("$.data.recommendedTests")

                // test1 checked method1 and method2 in build1, but method2 was modified in build2,
                // that's why test1 should be recommended to run in build2
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
            }
        }
    }

    @Test
    fun `given test that covers unmodified methods of one build and modified methods of another one, recommended test service should suggest skipping it`() {
        runBlocking {
            val client = runDrillApplication().apply {
                //build1
                deployInstance(build1, arrayOf(method1, method2))
                launchTest(test1, build1, arrayOf(method1 to probesOf(1, 1), method2 to probesOf(0, 0, 0)))
                //build2
                val modifiedMethod1 = method1.changeChecksum()
                deployInstance(build2, arrayOf(modifiedMethod1, method2))
                launchTest(test1, build2, arrayOf(modifiedMethod1 to probesOf(1, 1), method2 to probesOf(0, 0, 0)))
                //build3
                deployInstance(build3, arrayOf(modifiedMethod1, method2))
            }

            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("testsToSkip", true)
                parameter("targetBuildVersion", build3.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val recommendedTests = json.read<List<Map<String, Any>>>("$.data.recommendedTests")

                // test1 already checked method1 in build2 and method1 was not modified in build3,
                // that's why test1 should be recommended to skip in build3,
                // despite test1 also checked method1 in build1 and method1 was modified in build2
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
            }
        }
    }

    @Test
    fun `given test that checks methods have no changes compared to baseline, recommended test service should suggest skipping it`() {
        runBlocking {
            val client = runDrillApplication().apply {
                //build1
                deployInstance(build1, arrayOf(method1, method2, method3))
                launchTest(test1, build1, arrayOf(method1 to probesOf(1, 1), method2 to probesOf(1, 1, 1), method3 to probesOf(0)))
                //build2
                val modifiedMethod2 = method2.changeChecksum()
                deployInstance(build2, arrayOf(method1, modifiedMethod2, method3))
                //build3
                deployInstance(build3, arrayOf(method1, modifiedMethod2, method3.changeChecksum()))
            }

            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("testsToSkip", true)
                parameter("targetBuildVersion", build3.buildVersion)
                parameter("baselineBuildVersion", build2.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val recommendedTests = json.read<List<Map<String, Any>>>("$.data.recommendedTests")

                // even though test1 checked method2 in build1 and method2 was changed in build2 and build3,
                // test1 should be recommended to be skipped in build3 because compared to build2, build3 has no changes in method2
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test1.definitionId })
            }
        }
    }

    @AfterEach
    fun clearAll() = withTransaction {
        CoverageTable.deleteAll()
        InstanceTable.deleteAll()
        MethodTable.deleteAll()
        BuildTable.deleteAll()
        TestLaunchTable.deleteAll()
        TestSessionTable.deleteAll()
        TestDefinitionTable.deleteAll()
    }

    private fun runDrillApplication() =
        drillApplication(rawDataServicesDIModule, metricsDIModule) {
            dataIngestRoutes()
            metricsRoutes()
        }.drillClient()
}

