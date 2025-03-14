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
import com.epam.drill.admin.writer.rawdata.table.*
import com.jayway.jsonpath.JsonPath
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildDiffReportApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    @Test
    fun `given builds with different methods, build-diff-report service should calculate total changes`() {
        runBlocking {
            val client = runDrillApplication().apply {
                //build1 has 2 methods
                deployInstance(build1, arrayOf(method1, method2))
                //build2 has 1 new method, 1 modified method and 1 deleted method compared to build1
                deployInstance(build2, arrayOf(method2.changeChecksum(), method3))
            }

            client.get("/metrics/build-diff-report") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", build2.buildVersion)
                parameter("baselineBuildVersion", build1.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val metrics = json.read<Map<String, Any>>("$.data.metrics")

                assertEquals(1, metrics["changes_new_methods"])
                assertEquals(1, metrics["changes_modified_methods"])
                assertEquals(2, metrics["total_changes"])
            }
        }
    }

    @Test
    fun `given tests on target build, build-diff-report service should calculate isolated coverage`() {
        runBlocking {
            val client = runDrillApplication().apply {
                // build1 has 2 methods
                deployInstance(build1, arrayOf(method1, method2))
                // build2 has 1 modified method and 1 new method compared to build1.
                // test1 covers method1 and method2, test2 covers method3
                deployInstance(build2, arrayOf(method1, method2.changeChecksum(), method3))
                launchTest(
                    session1, test1, build2, arrayOf(
                        method1 to probesOf(1, 1),
                        method2 to probesOf(1, 0, 0),
                        method3 to probesOf(0)
                    )
                )
                launchTest(
                    session1, test2, build2, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(0, 0, 0),
                        method3 to probesOf(1)
                    )
                )
            }

            client.get("/metrics/build-diff-report") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", build2.buildVersion)
                parameter("baselineBuildVersion", build1.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val metrics = json.read<Map<String, Any>>("$.data.metrics")

                // test1 covers method2 by 1 of 3 probes,
                // test2 covers method3 by 1 of 1 probes,
                // coverage in method1 is not considered because method1 was not changed,
                // so total coverage is 2 of 4 probes
                assertEquals(0.5, metrics["coverage"])
            }
        }
    }

    @Test
    fun `given tests on different builds, build-diff-report service should calculate aggregated coverage`() {
        runBlocking {
            val client = runDrillApplication().apply {
                //build1 has 1 method, test1 covers it
                deployInstance(build1, arrayOf(method1))
                launchTest(
                    session1, test1, build1, arrayOf(
                        method1 to probesOf(1, 1)
                    )
                )
                //build2 has 2 new methods compared to build1, test2 covers method2 and method3
                deployInstance(build2, arrayOf(method1, method2, method3))
                launchTest(
                    session2, test2, build2, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(1, 0, 0),
                        method3 to probesOf(1)
                    )
                )
                //build3 has 1 modified method compared to build2, test3 covers method2
                deployInstance(build3, arrayOf(method1, method2, method3.changeChecksum()))
                launchTest(
                    session3, test3, build3, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(0, 1, 0),
                        method3 to probesOf(0)
                    )
                )
            }

            client.get("/metrics/build-diff-report") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", build3.buildVersion)
                parameter("baselineBuildVersion", build1.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val metrics = json.read<Map<String, Any>>("$.data.metrics")

                // test2 covers method2 by 1 of 3 probes,
                // test3 covers method2 by 1 of 3 probes but different probes compared to test2,
                // coverage in method1 is not considered because method1 was not changed,
                // coverage collected by test2 in method3 is not considered because method3 was changed,
                // so total coverage is 2 of 4 probes
                assertEquals(0.5, metrics["coverage"])
            }
        }
    }

    @Test
    fun `given tests on different builds, build-diff-report service should calculate recommended tests to run`() {
        runBlocking {
            val client = runDrillApplication().apply {
                //build1 has 1 method, test1 covers it
                deployInstance(build1, arrayOf(method1))
                launchTest(
                    session1, test1, build1, arrayOf(
                        method1 to probesOf(1, 1)
                    )
                )
                //build2 has 2 new methods compared to build1, test2 covers method2 and method3
                deployInstance(build2, arrayOf(method1, method2, method3))
                launchTest(
                    session2, test2, build2, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(1, 0, 0),
                        method3 to probesOf(1)
                    )
                )
                //build3 has 1 modified method compared to build2, test3 covers method3
                deployInstance(build3, arrayOf(method1, method2, method3.changeChecksum()))
                launchTest(
                    session3, test3, build3, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(0, 0, 0),
                        method3 to probesOf(1)
                    )
                )
            }

            client.get("/metrics/build-diff-report") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", build3.buildVersion)
                parameter("baselineBuildVersion", build1.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val metrics = json.read<Map<String, Any>>("$.data.metrics")

                // coverage in method1 is not considered because method1 was not changed,
                // test1 is not recommended to run because it covers only method1,
                // test2 is recommended to run because it covers method3, but method3 was changed in build3,
                // test3 is not recommended to run because it has already been run on build3,
                // so total recommended tests is 1
                assertEquals(1, metrics["recommended_tests"])
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
}