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
import com.epam.drill.admin.test.MetricsDatabaseTests
import com.epam.drill.admin.test.withTransaction
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import io.ktor.client.request.get
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupsApiTest : MetricsDatabaseTests({ default, metrics ->
    RawDataWriterDatabaseConfig.init(default)
    MetricsDatabaseConfig.init(metrics)
}) {
    private suspend fun TestDataDsl.initTestData() {
        client.putBuild(BuildPayload(groupId = testGroup, appId = "app-1", buildVersion = "1.0.0"))
        client.putBuild(BuildPayload(groupId = testGroup, appId = "app-2", buildVersion = "0.1.0"))
        client.putBuild(BuildPayload(groupId = "group-2", appId = "app-3", buildVersion = "0.0.1"))
    }

    @Test
    fun `get groups should return distinct group ids ordered`() = havingData {
        initTestData()
    }.expectThat {
        client.get("/metrics/groups").returnsStrings { data ->
            assertEquals(listOf("group-1", "group-2"), data)
        }
    }

    @AfterEach
    fun clearAll() = withTransaction(RawDataWriterDatabaseConfig.database) {
        BuildTable.deleteAll()
    }
}
