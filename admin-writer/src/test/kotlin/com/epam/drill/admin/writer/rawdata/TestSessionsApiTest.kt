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

import com.epam.drill.admin.writer.rawdata.route.putTestSessions
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import com.epam.drill.admin.test.*
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class TestSessionsApiTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {

    @Test
    fun `given new test session, put test sessions service should save test session in database and return OK`() = withRollback {
        val testGroup = "test-group"
        val testSession = "test-session-1"
        val timeBeforeTest = LocalDateTime.now()
        val app = drillApplication(rawDataServicesDIModule) {
            putTestSessions()
        }

        app.client.put("/sessions") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                    "id": "$testSession",
                    "groupId": "$testGroup",                    
                    "testTaskId": "test-task-1",
                    "startedAt": "2025-01-01T00:00:00+01:00"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertJsonEquals(
                """
                {
                    "message": "Test sessions saved"
                }
            """.trimIndent(), bodyAsText()
            )
        }

        val savedTestSessions = TestSessionTable.selectAll()
            .filter { it[TestSessionTable.groupId] == testGroup }
            .filter { it[TestSessionTable.id].value == testSession }
        assertEquals(1, savedTestSessions.size)
        savedTestSessions.forEach {
            assertNotNull(it[TestSessionTable.testTaskId])
            assertNotNull(it[TestSessionTable.startedAt])
            assertTrue(it[TestSessionTable.createdAt] >= timeBeforeTest)
        }
    }
}