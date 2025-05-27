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
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
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
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    @Test
    fun `given build with no coverage, coverage service should return methods list with zero coverage`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                deployInstance(build1, arrayOf(method1, method2))
            }
            client.get("/metrics/coverage") {
                parameter("buildId", "$testGroup:$testApp:1.0.0")
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(2, data.size)
                assertTrue(data.all { it["coveredProbes"] == 0 })
            }
        }

    @Test
    fun `given build with coverage, coverage service should return methods list with coverage data`(): Unit =
        runBlocking {
            val client = runDrillApplication {
                deployInstance(build1, arrayOf(method1, method2))
                launchTest(
                    session1, test1, build1, arrayOf(
                        method1 to probesOf(1, 1),
                        method2 to probesOf(0, 0, 1)
                    )
                )
            }
            client.get("/metrics/coverage") {
                parameter("buildId", "$testGroup:$testApp:1.0.0")
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(2, data.size)
                assertTrue(data.any { it["name"] == method1.name && it["coveredProbes"] == 2 })
                assertTrue(data.any { it["name"] == method2.name && it["coveredProbes"] == 1 })
            }
        }

    @Test
    fun `given page and size, coverage service should return methods only for specified page and size`(): Unit =
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
            }

            client.get("/metrics/coverage") {
                parameter("buildId", "$testGroup:$testApp:1.0.0")
                parameter("page", 1)
                parameter("pageSize", 10)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertEquals(10, data.size)
                val total = json.read<Int>("$.paging.total")
                assertEquals(15, total)
            }

            client.get("/metrics/coverage") {
                parameter("buildId", "$testGroup:$testApp:1.0.0")
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