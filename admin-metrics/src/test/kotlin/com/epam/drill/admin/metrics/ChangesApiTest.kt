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
import com.epam.drill.admin.metrics.views.ChangeType
import com.epam.drill.admin.test.*
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.CoverageTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import com.jayway.jsonpath.JsonPath
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangesApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    private suspend fun HttpClient.initBuildsAndMethodsData() {
        deployInstance(instance = build1, methods = arrayOf(method1, method2))
        deployInstance(
            build2, methods = arrayOf(
                method1, //same as in build1
                method2.changeChecksum() //modified
            )
        )
        deployInstance(
            instance = build3, methods = arrayOf(
                method1, //same as in build1
                method2.changeChecksum(), //same as in build2
                method3 //added
            )
        )
    }

    @Test
    fun `given target and baseline build, changes service should return method changes between builds`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                initBuildsAndMethodsData()
            }

            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "3.0.0")
                parameter("baselineBuildVersion", "1.0.0")
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(2, data.size)
                assertTrue(data.any { it["name"] == method2.name && it["changeType"] == ChangeType.MODIFIED.name })
                assertTrue(data.any { it["name"] == method3.name && it["changeType"] == ChangeType.NEW.name })
                assertTrue(data.all { (it["coveredProbes"] as Int) == 0 })
            }
        }

    @Test
    fun `given isolated tested target build, changes service should return method changes with coverage in current build`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                initBuildsAndMethodsData()
                launchTest(
                    session1, test1, build3, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(1, 1, 0), //method2 covered by 2 probes
                        method3 to probesOf(0),
                    )
                )
            }

            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "3.0.0")
                parameter("baselineBuildVersion", "1.0.0")
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(2, data.size)
                assertTrue(data.any { it["name"] == method2.name && (it["coveredProbes"] as Int) == 2 })
            }
        }

    @Test
    fun `given aggregated tested builds, changes service should return method changes with coverage in other builds`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                initBuildsAndMethodsData()

                launchTest(
                    session1, test1, build2, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(1, 1, 0)
                    )
                )

                launchTest(
                    session2, test2, build3, arrayOf(
                        method1 to probesOf(0, 0),
                        method2 to probesOf(0, 0, 1),
                        method3 to probesOf(0)
                    )
                )
            }

            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "3.0.0")
                parameter("baselineBuildVersion", "1.0.0")
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(2, data.size)
                assertTrue(data.any { it["name"] == method2.name && (it["coveredProbes"] as Int) == 1 })
                assertTrue(data.any { it["name"] == method2.name && (it["coveredProbesInOtherBuilds"] as Int) == 3 })
            }
        }

    @Test
    fun `given page and size, get changes endpoint should return changes only for specified page and size`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                val methods = (1..15).map { idx ->
                    SingleMethodPayload(
                        classname = testClass,
                        name = "method$idx",
                        params = "()",
                        returnType = "void",
                        probesCount = 3,
                        probesStartPos = 3 * (idx - 1),
                        bodyChecksum = "100$idx",
                    )
                }
                deployInstance(instance = build1, methods = methods.toTypedArray())
                deployInstance(instance = build2, methods = methods.map { it.changeChecksum() }.toTypedArray())
            }

            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "2.0.0")
                parameter("baselineBuildVersion", "1.0.0")
                parameter("page", 1)
                parameter("pageSize", 5)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(5, data.size)
                val total = json.read<Int>("$.paging.total")
                assertEquals(15, total)
            }

            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "2.0.0")
                parameter("baselineBuildVersion", "1.0.0")
                parameter("page", 2)
                parameter("pageSize", 10)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(5, data.size)
                val total = json.read<Int>("$.paging.total")
                assertEquals(15, total)
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
