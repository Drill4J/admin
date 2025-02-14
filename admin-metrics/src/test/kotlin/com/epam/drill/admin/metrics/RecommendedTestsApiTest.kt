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
                launchTest(test1, build1, arrayOf(method1 to probesOf(1, 1), method2 to probesOf(0, 0, 0)))
                launchTest(test2, build1, arrayOf(method1 to probesOf(0, 0), method2 to probesOf(1, 1, 1)))
                deployInstance(build2, arrayOf(method1, method2.changeChecksum(), method3))
            }

            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("targetBuildVersion", build2.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val recommendedTests = json.read<List<Map<String, Any>>>("$.data.recommendedTests")
                assertEquals(1, recommendedTests.size)
                assertTrue(recommendedTests.any { it["testDefinitionId"] == test2.definitionId })
            }
        }
    }

    @Test
    fun `given test that covers unmodified methods, recommended test service should suggest skipping it`() {
        runBlocking {
            val client = runDrillApplication().apply {
                deployInstance(build1, arrayOf(method1, method2))
                launchTest(test1, build1, arrayOf(method1 to probesOf(1, 1), method2 to probesOf(0, 0, 0)))
                launchTest(test2, build1, arrayOf(method1 to probesOf(0, 0), method2 to probesOf(1, 1, 1)))
                deployInstance(build2, arrayOf(method1, method2.changeChecksum(), method3))
            }

            client.get("/metrics/recommended-tests") {
                parameter("groupId", testGroup)
                parameter("appId", testApp)
                parameter("testsToSkip", true)
                parameter("targetBuildVersion", build2.buildVersion)
            }.assertSuccessStatus().apply {
                val json = JsonPath.parse(bodyAsText())
                val recommendedTests = json.read<List<Map<String, Any>>>("$.data.recommendedTests")
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

