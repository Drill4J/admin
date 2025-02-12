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

import com.epam.drill.admin.writer.rawdata.route.postCoverage
import com.epam.drill.admin.writer.rawdata.table.CoverageTable
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageApiTest : DatabaseTests() {

    @Test
    fun `given coverage data, post coverage service should save coverage in database and return OK`() = withRollback {
        val testGroup = "test-group"
        val testApp = "test-app"
        val testInstance = "test-instance"
        val testClassname = "com.example.TestClass"
        val testTestId = "test-id"
        val timeBeforeTest = LocalDateTime.now()
        val app = dataIngestApplication {
            routing {
                postCoverage()
            }
        }

        app.client.post("/coverage") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                    "groupId": "$testGroup",
                    "appId": "$testApp",
                    "instanceId": "$testInstance",
                    "coverage": [
                        {
                            "classname": "$testClassname",
                            "testId": "$testTestId",
                            "probes": [true, false, true]
                        },
                        {
                            "classname": "$testClassname",
                            "testId": "$testTestId",
                            "probes": [false, true, false]
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
                    "message": "Coverage saved"
                }
            """.trimIndent(), bodyAsText()
            )
        }

        val savedCoverage = CoverageTable.selectAll().asSequence()
            .filter { it[CoverageTable.groupId] == testGroup }
            .filter { it[CoverageTable.appId] == testApp }
            .filter { it[CoverageTable.instanceId] == testInstance }
            .filter { it[CoverageTable.classname] == testClassname }
            .filter { it[CoverageTable.testId] == testTestId }
            .toList()
        assertEquals(2, savedCoverage.size)
        savedCoverage.forEach {
            assertTrue(it[CoverageTable.createdAt] >= timeBeforeTest)
        }
    }
}