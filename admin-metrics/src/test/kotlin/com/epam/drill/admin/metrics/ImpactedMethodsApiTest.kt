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
import com.epam.drill.admin.writer.rawdata.route.payload.TestDetails
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.CoverageTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import com.jayway.jsonpath.JsonPath
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImpactedMethodsApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    @Test
    fun `given baseline build tests, impacted methods service should return only impacted methods compared to baseline`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                // build1 has method1 and method2
                deployInstance(build1, arrayOf(method1, method2))
                // test1 hits method1 on build1
                launchTest(
                    session1, test1, build1, arrayOf(
                        method1 to probesOf(0, 1),
                        method2 to probesOf(0, 0, 0)
                    )
                )
                // test2 hits method2 on build1
                launchTest(
                    session1, test2, build1, arrayOf(
                        method1 to probesOf(0, 0), method2 to probesOf(0, 0, 1)
                    )
                )
                // build2 has changed method2 compared to build1
                deployInstance(
                    build2, arrayOf(
                        method1, method2.changed()
                    )
                )
            }

            // Check impacted methods in build2 compared to build1
            client.get("/metrics/impacted-methods") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build2.buildVersion)
                parameter("baselineBuildVersion", build1.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertTrue(data.isNotEmpty())
                assertTrue(data.any { it["name"] == method2.name })
                assertTrue(data.any { (it["impactedTests"] as Int) == 1 })
            }
        }

    @Test
    fun `given partial baseline build tests, impacted methods service should return missing impacted methods from other builds`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                // build1 has method1 and method2
                deployInstance(build1, arrayOf(method1, method2))
                // test1 hits method1 on build1
                launchTest(
                    session1, test1, build1, arrayOf(
                        method1 to probesOf(0, 1),
                        method2 to probesOf(0, 0, 0)
                    )
                )
                // test2 hits method2 on build1
                launchTest(
                    session1, test2, build1, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(0, 0, 1)
                    )
                )
                // build2 has changed method2 compared to build1
                deployInstance(build2, arrayOf(method1, method2.changed()))
                launchTest(
                    session2, test2, build2, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(0, 0, 1) // test2 hits changed method2
                    )
                )
                // build3 has changed method1 compared to build2
                deployInstance(build3, arrayOf(method1.changed(), method2.changed()))
            }

            // Check impacted methods for build3 against build2
            client.get("/metrics/impacted-methods") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build3.buildVersion)
                parameter("baselineBuildVersion", build2.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(1, data.size)
                assertTrue(data.any { it["name"] == method1.name })
                assertTrue(data.any { (it["impactedTests"] as Int) == 1 })
            }
        }

    @Test
    fun `given partial baseline build tests with onlyBaselineBuildTestsEnabled, impacted methods service should return impacted methods from only baseline build tests`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                // build1 has method1 and method2
                deployInstance(build1, arrayOf(method1, method2))
                // test1 hits method1 on build1
                launchTest(
                    session1, test1, build1, arrayOf(
                        method1 to probesOf(0, 1),
                        method2 to probesOf(0, 0, 0)
                    )
                )
                // test2 hits method2 on build1
                launchTest(
                    session1, test2, build1, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(0, 0, 1)
                    )
                )
                // build2 has changed method2 compared to build1
                deployInstance(build2, arrayOf(method1, method2.changed()))
                // test2 hits changed method2 on build2
                launchTest(
                    session2, test2, build2, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(0, 0, 1)
                    )
                )
                // build3 has changed method1 compared to build2
                deployInstance(build3, arrayOf(method1.changed(), method2.changed()))
            }

            // Check impacted methods for build3 against build2 with onlyBaselineBuildTestsEnabled=true
            client.get("/metrics/impacted-methods") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build3.buildVersion)
                parameter("baselineBuildVersion", build2.buildVersion)
                parameter("onlyBaselineBuildTestsEnabled", true)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(0, data.size)
            }
        }

    @Test
    fun `given page and size, impacted methods service should return methods only for specified page and size`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                deployInstance(build1, arrayOf(method1, method2))
                // Create multiple test sessions with coverage to generate impacted methods
                for (i in 1..15) {
                    val testName = "test$i"
                    launchTest(
                        session1, TestDetails(testName = testName), build1, arrayOf(
                            method1 to probesOf(1),
                            method2 to probesOf(1, 1)
                        )
                    )
                }
                deployInstance(build2, arrayOf(method1.changeChecksum(), method2.changeChecksum()))
            }

            client.get("/metrics/impacted-methods") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build2.buildVersion)
                parameter("baselineBuildVersion", build1.buildVersion)
                parameter("page", 1)
                parameter("pageSize", 10)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                // Should return at most 10 records
                assertTrue(data.size <= 10)
            }

            client.get("/metrics/impacted-methods") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build2.buildVersion)
                parameter("baselineBuildVersion", build1.buildVersion)
                parameter("page", 2)
                parameter("pageSize", 10)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                // The second page should contain the remaining methods (5 or fewer)
                assertTrue(data.size <= 5)
            }
        }

    @Test
    fun `given test filter, impacted methods service should return only methods matching path and name`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                deployInstance(build1, arrayOf(method1, method2))
                // Create test sessions with different paths
                launchTest(
                    session1, TestDetails(testName = "test1", path = "com/example/path1/Test1"), build1, arrayOf(
                        method1 to probesOf(1),
                        method2 to probesOf(0, 0)
                    )
                )
                launchTest(
                    session1, TestDetails(testName = "test2", path = "com/example/path1/Test2"), build1, arrayOf(
                        method1 to probesOf(0),
                        method2 to probesOf(1, 1)
                    )
                )
                launchTest(
                    session1, TestDetails(testName = "test2", path = "com/example/other_path/Test3"), build1, arrayOf(
                        method1 to probesOf(1),
                        method2 to probesOf(1, 1)
                    )
                )
                deployInstance(build2, arrayOf(method1.changeChecksum(), method2.changeChecksum()))
            }

            client.get("/metrics/impacted-methods") {
                parameter("groupId", build1.groupId)
                parameter("appId", build1.appId)
                parameter("buildVersion", build2.buildVersion)
                parameter("baselineBuildVersion", build1.buildVersion)
                parameter("testPath", "com/example/path1")
                parameter("testName", "test2")
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertTrue(data.isNotEmpty())
                assertTrue(data.any { it["name"] == method2.name })
            }
        }

    @kotlin.test.AfterTest
    fun clearAll() = withTransaction {
        CoverageTable.deleteAll()
        InstanceTable.deleteAll()
        MethodTable.deleteAll()
        BuildTable.deleteAll()
        TestLaunchTable.deleteAll()
        TestSessionTable.deleteAll()
        TestDefinitionTable.deleteAll()
    }
}

