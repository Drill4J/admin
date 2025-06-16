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
import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.jayway.jsonpath.JsonPath
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationsApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    private suspend fun HttpClient.initTestData() {
        putBuild(BuildPayload(groupId = "group-1", appId = "app-1", buildVersion = "1.0.0"))
        putBuild(BuildPayload(groupId = "group-1", appId = "app-1", buildVersion = "2.0.0"))
        putBuild(BuildPayload(groupId = "group-1", appId = "app-2", buildVersion = "0.1.0"))
        putBuild(BuildPayload(groupId = "group-2", appId = "app-3", buildVersion = "0.0.1"))
    }

    @Test
    fun `given groupId, get applications service should return applications for specified group`(): Unit = runBlocking {
        val testGroup = "group-1"
        val client = runDrillApplication {
            initTestData()
        }

        client.get("/metrics/applications") {
            parameter("groupId", testGroup)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<List<Map<String, Any>>>("$.data")
            assertEquals(2, data.size)
            assertEquals(setOf("app-1", "app-2"), data.map { it["appId"] }.toSet())
        }
    }

    @Test
    fun `given no parameters, get applications service should return all applications`(): Unit = runBlocking {
        val client = runDrillApplication {
            initTestData()
        }

        client.get("/metrics/applications").apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<List<Map<String, Any>>>("$.data")
            assertEquals(3, data.size)
            assertEquals(setOf("app-1", "app-2", "app-3"), data.map { it["appId"] }.toSet())
        }
    }

    @AfterEach
    fun clearAll() = withTransaction {
        BuildTable.deleteAll()
    }

}