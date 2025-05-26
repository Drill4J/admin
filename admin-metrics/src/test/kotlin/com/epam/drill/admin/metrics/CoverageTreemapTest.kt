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
import com.epam.drill.admin.writer.rawdata.route.payload.InstancePayload
import com.epam.drill.admin.writer.rawdata.route.payload.MethodsPayload
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
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

class CoverageTreemapTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    @Test
    fun `given build with no coverage, coverage-treemap should return empty list`() {
        runBlocking {
            val client = runDrillApplication().apply {
                putBuild(BuildPayload(groupId = testGroup, appId = testApp, buildVersion = "1.0.0", branch = "main"))
            }
            val response = client.get("/metrics/coverage-treemap") {
                parameter("buildId", "${testGroup}:${testApp}:1.0.0")
            }
            response.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Any>>("$.data")
                assertTrue(data.isEmpty())
            }
        }
    }

    @Test
    fun `given build with methods and coverage, coverage-treemap should return non-empty list`() {
        runBlocking {
            val client = runDrillApplication().apply {
                putBuild(BuildPayload(groupId = testGroup, appId = testApp, buildVersion = "1.0.0", branch = "main"))
                putMethods(MethodsPayload(groupId = testGroup, appId = testApp, buildVersion = "1.0.0", methods = arrayOf(method1, method2)))
                // Simulate coverage for method1
                launchTest(session1, test1,
                    InstancePayload(
                        groupId = testGroup,
                        appId = testApp,
                        buildVersion = "1.0.0",
                        instanceId = "i1",
                        envId = "env-1"
                    ), arrayOf(method1 to probesOf(1, 1), method2 to probesOf(0, 0)))
            }
            val response = client.get("/metrics/coverage-treemap") {
                parameter("buildId", "${testGroup}:${testApp}:1.0.0")
            }
            response.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val data = json.read<List<Map<String, Any>>>("$.data")
                assertTrue(data.isNotEmpty())
                assertTrue(data.any { it["name"].toString().startsWith(method1.name) })
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
