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
package com.epam.drill.admin.writer.rawdata

import com.epam.drill.admin.writer.rawdata.route.settingsRoutes
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.test.*
import com.epam.drill.admin.writer.rawdata.config.settingsServicesDIModule
import com.epam.drill.admin.writer.rawdata.table.GroupSettingsTable
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRoutesTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {
    private val testGroup = "test-group"
    private lateinit var client: HttpClient

    @BeforeTest
    fun setUp() {
        this.client = drillApplication(settingsServicesDIModule) {
            settingsRoutes()
        }.client
    }

    @AfterTest
    fun tearDown(): Unit = transaction {
        GroupSettingsTable.deleteAll()
    }

    @Test
    fun `put group-settings service should save settings in database and return OK`(): Unit = runBlocking {
        client.put("group-settings/$testGroup") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "retentionPeriodDays": 30,
                    "metricsPeriodDays": 10                                        
                }
            """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        transaction {
            val savedSettings = GroupSettingsTable.selectAll().where { GroupSettingsTable.id eq testGroup }.single()
            assertEquals(30, savedSettings[GroupSettingsTable.retentionPeriodDays])
            assertEquals(10, savedSettings[GroupSettingsTable.metricsPeriodDays])
        }
    }

    @Test
    fun `get group-setting service should return saved settings from database`(): Unit = runBlocking {
        transaction {
            GroupSettingsTable.insert {
                it[id] = testGroup
                it[retentionPeriodDays] = 15
                it[metricsPeriodDays] = 5
            }
        }

        client.get("group-settings/$testGroup").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertJsonEquals(
                """
                {
                    "data": {
                        "retentionPeriodDays": 15,
                        "metricsPeriodDays": 5
                    }
                }
                """.trimIndent(),
                bodyAsText()
            )
        }
    }

    @Test
    fun `delete group-setting service should delete settings by group from database and return OK`(): Unit = runBlocking {
        transaction {
            GroupSettingsTable.insert {
                it[id] = testGroup
                it[retentionPeriodDays] = 20
                it[metricsPeriodDays] = 8
            }
        }

        client.delete("group-settings/$testGroup").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        transaction {
            assertTrue(GroupSettingsTable.selectAll().where { GroupSettingsTable.id eq testGroup }.empty())
        }
    }

}
