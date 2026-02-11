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
import com.epam.drill.admin.writer.rawdata.table.BuildMethodTable
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.MethodCoverageTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangesApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    private suspend fun TestDataDsl.initBuildsAndMethodsData() {
        build1 has listOf(method1, method2, method4)
        build2 hasModified method2 comparedTo build1
        build3 hasDeleted method4 comparedTo build2
        build3 hasNew method3 comparedTo build2
    }

    @Test
    fun `given target and baseline build, changes service should return method changes between builds`(): Unit =
        havingData {
            initBuildsAndMethodsData()
        }.expectThat {
            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "3.0.0")
                parameter("baselineBuildVersion", "1.0.0")
            }.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.any { it["name"] == method2.name && it["changeType"] == ChangeType.MODIFIED.name })
                assertTrue(data.any { it["name"] == method3.name && it["changeType"] == ChangeType.NEW.name })
                assertTrue(data.all { (it["coveredProbes"] as Int) == 0 })
            }
        }

    @Test
    fun `given isolated tested target build, changes service should return method changes with coverage in current build`(): Unit =
        havingData {
            initBuildsAndMethodsData()
            test1 covers method2 with probesOf(1, 1, 0) on build3
        }.expectThat {
            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "3.0.0")
                parameter("baselineBuildVersion", "1.0.0")
            }.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.any { it["name"] == method2.name && (it["coveredProbes"] as Int) == 2 })
            }
        }

    @Test
    fun `given aggregated tested builds, changes service should return method changes with coverage in other builds`(): Unit =
        havingData {
            initBuildsAndMethodsData()
            test1 covers method2 with probesOf(1, 1, 0) on build2
            test2 covers method2 with probesOf(0, 0, 1) on build3
        }.expectThat {
            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "3.0.0")
                parameter("baselineBuildVersion", "1.0.0")
            }.returns { data ->
                assertEquals(2, data.size)
                assertTrue(data.any { it["name"] == method2.name && (it["coveredProbes"] as Int) == 1 })
                assertTrue(data.any { it["name"] == method2.name && (it["coveredProbesInOtherBuilds"] as Int) == 3 })
            }
        }

    @Test
    fun `given includeDeleted parameter, changes service should return deleted methods between builds`(): Unit =
        havingData {
            initBuildsAndMethodsData()
        }.expectThat {
            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "3.0.0")
                parameter("baselineBuildVersion", "1.0.0")
                parameter("includeDeleted", true)
            }.returns { data ->
                assertTrue(data.any { it["name"] == method4.name && it["changeType"] == ChangeType.DELETED.name })
            }
        }

    @Test
    fun `given includeEqual parameter, changes service should return equal methods between builds`(): Unit =
        havingData {
            initBuildsAndMethodsData()
        }.expectThat {
            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "3.0.0")
                parameter("baselineBuildVersion", "1.0.0")
                parameter("includeEqual", true)
            }.returns { data ->
                assertTrue(data.any { it["name"] == method1.name && it["changeType"] == ChangeType.EQUAL.name })
            }
        }

    @Test
    fun `given page and size, get changes endpoint should return changes only for specified page and size`(): Unit =
        havingData {
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
            build1 has methods.toList()
            client.deployInstance(instance = build2, methods = methods.map { it.changeChecksum() }.toTypedArray())

        }.expectThat {
            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "2.0.0")
                parameter("baselineBuildVersion", "1.0.0")
                parameter("page", 1)
                parameter("pageSize", 5)
            }.returns { data ->
                assertEquals(5, data.size)
            }

            client.get("/metrics/changes") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("buildVersion", "2.0.0")
                parameter("baselineBuildVersion", "1.0.0")
                parameter("page", 2)
                parameter("pageSize", 10)
            }.returns { data ->
                assertEquals(5, data.size)
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
