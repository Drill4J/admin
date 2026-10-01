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

import com.epam.drill.admin.writer.rawdata.route.putTestSessionHeartbeat
import com.epam.drill.admin.writer.rawdata.route.putTestSessions
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import com.epam.drill.admin.test.*
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import com.epam.drill.admin.writer.rawdata.route.payload.TestSessionStatus
import com.epam.drill.admin.writer.rawdata.table.TestSessionBuildTable
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class TestSessionsApiTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {

    private val testExistingGroup = "test-old-group"
    private val testExistingProjectId = "test-old-project"
    private val testExistingSession = "test-old-session"

    @BeforeEach
    fun setUp() {
        transaction {
            transaction {
                TestSessionTable.insert {
                    it[id] = testExistingSession
                    it[groupId] = testExistingGroup
                    it[testProjectId] = testExistingProjectId
                    it[startedAt] = LocalDateTime.now()
                    it[status] = TestSessionStatus.RUNNING.name
                }
            }

        }
    }

    @AfterEach
    fun tearDown() {
        transaction {
            TestSessionTable.deleteWhere { id eq testExistingSession }
        }
    }

    @Test
    fun `given new test session, put test sessions service should save test session in database and return OK`() =
        withRollback {
            val testGroup = "test-group"
            val testProjectId = "test-project-id"
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
                    "testProjectId": "$testProjectId",
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

            waitUntilInTransaction {
                val savedTestSessions = TestSessionTable.selectAll()
                    .filter { it[TestSessionTable.groupId] == testGroup }
                    .filter { it[TestSessionTable.testProjectId] == testProjectId }
                    .filter { it[TestSessionTable.id].value == testSession }
                assertEquals(1, savedTestSessions.size)
                savedTestSessions.forEach {
                    assertNotNull(it[TestSessionTable.testTaskId])
                    assertNotNull(it[TestSessionTable.startedAt])
                    assertTrue(it[TestSessionTable.createdAt] >= timeBeforeTest)
                }
            }
        }

    @Test
    fun `given new test session with builds, put test sessions service should save session builds in database and return OK`() = withRollback {
        val testGroup = "test-group"
        val testProjectId = "test-project-id"
        val testSession = "test-session-2"
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
                    "testProjectId": "$testProjectId",
                    "testTaskId": "test-task-2",
                    "startedAt": "2025-01-01T00:00:00+01:00",
                    "builds": [
                        {
                            "appId": "test-app-1",
                            "buildVersion": "1.0.0"
                        },
                        {
                            "appId": "test-app-2",
                            "buildVersion": "1.1.0"
                        }
                    ]
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

        waitUntilInTransaction {
            val savedSessionBuilds = TestSessionBuildTable.selectAll()
                .filter { it[TestSessionBuildTable.testSessionId] == testSession }
            assertEquals(2, savedSessionBuilds.size)
            savedSessionBuilds.forEach {
                assertNotNull(it[TestSessionBuildTable.buildId])
                assertNotNull(it[TestSessionBuildTable.groupId])
                assertTrue(it[TestSessionBuildTable.createdAt] >= timeBeforeTest)
            }
        }
    }

    @Test
    fun `given existing test session, put test session heartbeat with FINISHED status should record status and return OK`() {
        runBlocking {
            val app = drillApplication(rawDataServicesDIModule) {
                putTestSessionHeartbeat()
            }

            app.client.put("/sessions/heartbeat") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    """
                {
                    "groupId": "$testExistingGroup",
                    "testProjectId": "$testExistingProjectId",
                    "testSessionId": "$testExistingSession",
                    "status": "FINISHED"
                }
                    """.trimIndent()
                )
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
                assertJsonEquals(
                    """
                {
                    "message": "Test session heartbeat saved"
                }
                    """.trimIndent(), bodyAsText()
                )
            }

            waitUntilInTransaction {
                val savedSession = TestSessionTable.selectAll().first {
                    it[TestSessionTable.id].value == testExistingSession
                }
                assertNotNull(savedSession[TestSessionTable.lastHeartbeatAt])
                assertEquals(TestSessionStatus.FINISHED.name, savedSession[TestSessionTable.status])
            }
        }
    }
}