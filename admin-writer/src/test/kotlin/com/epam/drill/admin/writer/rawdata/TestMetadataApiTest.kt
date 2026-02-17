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

import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.assertJsonEquals
import com.epam.drill.admin.test.drillApplication
import com.epam.drill.admin.test.withRollback
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import com.epam.drill.admin.writer.rawdata.route.postTestDefinitions
import com.epam.drill.admin.writer.rawdata.route.postTestLaunches
import com.epam.drill.admin.writer.rawdata.route.postTestMetadata
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestMetadataApiTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {

    @Test
    fun `given test definition and test launches, post test metadata service should return OK and save new test metadata in database`() = withRollback {
        val testGroup = "test-group"
        val testSession = "test-session"
        val testLaunch1 = "test-launch-1"
        val testLaunch2 = "test-launch-2"
        val testDefinition = "test-definition-1"
        val testRunner = "JUnit"
        val testName = "test-1"
        val testPath = "com.example.TestClass"
        val timeBeforeTest = LocalDateTime.now()
        val app = drillApplication(rawDataServicesDIModule) {
            postTestMetadata()
        }

        app.client.post("/tests-metadata") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                    "groupId": "$testGroup",
                    "sessionId": "$testSession",
                    "tests": [
                        {
                            "testLaunchId": "$testLaunch1",
                            "testDefinitionId": "$testDefinition",
                            "result": "PASSED",
                            "duration": 123,
                            "details": {
                                "runner": "$testRunner",
                                "testName": "$testName",
                                "path": "$testPath",
                                "tags": ["tag-1", "tag-2"],
                                "metadata": { 
                                    "key1": "value1",
                                    "key2": "value2"
                                }
                            }
                        },
                        {
                            "testLaunchId": "$testLaunch2",
                            "testDefinitionId": "$testDefinition",
                            "result": "FAILED",
                            "duration": 345,
                            "details": {
                                "runner": "$testRunner",
                                "testName": "$testName",
                                "path": "$testPath",
                                "tags": ["tag-1", "tag-2"],
                                "metadata": { 
                                    "key1": "value1",
                                    "key2": "value2"
                                }
                            }
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
                    "message": "Test metadata saved"
                }
            """.trimIndent(), bodyAsText()
            )
        }

        val savedTestLaunches = TestLaunchTable.selectAll()
            .filter { it[TestLaunchTable.groupId] == testGroup }
            .filter { it[TestLaunchTable.testSessionId] == testSession }
            .filter { it[TestLaunchTable.testDefinitionId] == testDefinition }
        assertEquals(2, savedTestLaunches.size)
        savedTestLaunches.forEach {
            assertNotNull(it[TestLaunchTable.duration])
            assertNotNull(it[TestLaunchTable.result])
            assertTrue(it[TestLaunchTable.createdAt] >= timeBeforeTest)
        }

        val savedTestDefinitions = TestDefinitionTable.selectAll()
            .filter { it[TestDefinitionTable.groupId] == testGroup }
            .filter { it[TestDefinitionTable.id].value == testDefinition }
        assertEquals(1, savedTestDefinitions.size)
        savedTestDefinitions.forEach {
            assertNotNull(it[TestDefinitionTable.runner])
            assertNotNull(it[TestDefinitionTable.name])
            assertNotNull(it[TestDefinitionTable.path])
            assertEquals(2, it[TestDefinitionTable.tags]?.size)
            assertEquals(2, it[TestDefinitionTable.metadata]?.jsonObject?.size)
            assertTrue(it[TestDefinitionTable.createdAt] >= timeBeforeTest)
        }
    }

    @Test
    fun `given test definitions payload, post test definitions should return OK and save definitions`() = withRollback {
        val testGroup = "group-1"
        val testDefinition1 = "def-1"
        val testDefinition2 = "def-2"
        val timeBeforeTest = LocalDateTime.now()

        val app = drillApplication(rawDataServicesDIModule) {
            postTestDefinitions()
        }

        app.client.post("/test-definitions") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
            {
                "groupId": "$testGroup",
                "definitions": [
                    {
                        "id": "$testDefinition1",
                        "runner": "JUnit",
                        "name": "test-1",
                        "type": "UNIT",
                        "path": "com.example.Test1",
                        "tags": ["tag-1"],
                        "metadata": { "k1": "v1" }
                    },
                    {
                        "id": "$testDefinition2",
                        "runner": "JUnit",
                        "name": "test-2",
                        "type": "UNIT",
                        "path": "com.example.Test2",
                        "tags": ["tag-2"],
                        "metadata": { "k2": "v2" }
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
                "message": "Test definitions saved"
            }
            """.trimIndent(),
                bodyAsText()
            )
        }

        val saved = TestDefinitionTable.selectAll()
            .filter { it[TestDefinitionTable.groupId] == testGroup }

        assertEquals(2, saved.size)
        saved.forEach {
            assertNotNull(it[TestDefinitionTable.runner])
            assertNotNull(it[TestDefinitionTable.name])
            assertTrue(it[TestDefinitionTable.createdAt] >= timeBeforeTest)
        }
    }

    @Test
    fun `given test launches payload, post test launches should return OK and save launches`() = withRollback {
        val testGroup = "group-1"
        val testSession = "session-1"
        val testDefinition = "def-1"
        val launch1 = "launch-1"
        val launch2 = "launch-2"
        val timeBeforeTest = LocalDateTime.now()

        val app = drillApplication(rawDataServicesDIModule) {
            postTestLaunches()
        }

        app.client.post("/test-launches") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
            {
                "groupId": "$testGroup",
                "testSessionId": "$testSession",
                "launches": [
                    {
                        "id": "$launch1",
                        "testDefinitionId": "$testDefinition",
                        "result": "PASSED",
                        "duration": 100
                    },
                    {
                        "id": "$launch2",
                        "testDefinitionId": "$testDefinition",
                        "result": "FAILED",
                        "duration": 200
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
                "message": "Test launches saved"
            }
            """.trimIndent(),
                bodyAsText()
            )
        }

        val saved = TestLaunchTable.selectAll()
            .filter { it[TestLaunchTable.groupId] == testGroup }
            .filter { it[TestLaunchTable.testSessionId] == testSession }
            .filter { it[TestLaunchTable.testDefinitionId] == testDefinition }

        assertEquals(2, saved.size)
        saved.forEach {
            assertNotNull(it[TestLaunchTable.result])
            assertNotNull(it[TestLaunchTable.duration])
            assertTrue(it[TestLaunchTable.createdAt] >= timeBeforeTest)
        }
    }


}