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
import com.epam.drill.admin.test.*
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import com.epam.drill.admin.writer.rawdata.route.dataIngestRoutes
import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.jayway.jsonpath.JsonPath
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildsInfoApiTest : DatabaseTests({
    RawDataWriterDatabaseConfig.init(it)
    MetricsDatabaseConfig.init(it)
}) {
    private suspend fun HttpClient.initTestData() {
        putBuild(BuildPayload(groupId = "group-1", appId = "app-1", buildVersion = "1.0.0", branch = "main"))
        putBuild(BuildPayload(groupId = "group-1", appId = "app-1", buildVersion = "2.0.0", branch = "main"))
        putBuild(BuildPayload(groupId = "group-1", appId = "app-1", buildVersion = "3.0.0", branch = "develop"))
        putBuild(BuildPayload(groupId = "group-1", appId = "app-2", buildVersion = "1.0.0", branch = "main"))
        putBuild(BuildPayload(groupId = "group-2", appId = "app-1", buildVersion = "1.0.0", branch = "main"))
    }


    @Test
    fun `given groupId and appId, get builds service should return builds only for specified group and app`(): Unit = runBlocking {
        val testGroup = "group-1"
        val testApp = "app-1"
        val client = runDrillApplication().apply {
            initTestData()
        }

        client.get("/metrics/builds") {
            parameter("groupId", testGroup)
            parameter("appId", testApp)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<List<Map<String, Any>>>("$.data")
            assertEquals(3, data.size)
            data.forEach { record ->
                assertEquals(testGroup, record["groupId"])
                assertEquals(testApp, record["appId"])
            }
        }
    }


    @Test
    fun `given groupId, appId, and branch, get metrics builds should return builds only for specified group, app and branch`(): Unit = runBlocking {
        val testGroup = "group-1"
        val testApp = "app-1"
        val testBranch = "main"
        val app = drillApplication(rawDataServicesDIModule, metricsDIModule) {
            dataIngestRoutes()
            metricsRoutes()
        }
        val client = app.drillClient().apply {
            initTestData()
        }

        client.get("/metrics/builds") {
            parameter("groupId", testGroup)
            parameter("appId", testApp)
            parameter("branch", testBranch)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = JsonPath.parse(bodyAsText())
            val data = json.read<List<Map<String, Any>>>("$.data")
            assertEquals(2, data.size)
            data.forEach { record ->
                assertEquals(testGroup, record["groupId"])
                assertEquals(testApp, record["appId"])
                assertEquals(testBranch, record["branch"])
            }
        }
    }

    @AfterEach
    fun clearAll() = withTransaction {
        BuildTable.deleteAll()
    }

}