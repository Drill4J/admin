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
import com.epam.drill.admin.writer.rawdata.table.MethodCoverageTable
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

class CoverageApiTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {

    @Test
    fun `given coverage data, post coverage service should save coverage in database and return OK`() = withRollback {
        val testGroup = "test-group"
        val testApp = "test-app"
        val testInstance = "test-instance"
        val testClassname = "com.example.TestClass"
        val testMethodSignature1 = "com.example.TestClass:myMethod:myParam:void"
        val testMethodSignature2 = "com.example.TestClass:myMethod2:myParam:void"
        val testTestId = "test-id"
        val timeBeforeTest = LocalDateTime.now()
        val app = drillApplication(rawDataServicesDIModule) {
            postCoverage()
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
                            "signature": "$testMethodSignature1",
                            "testId": "$testTestId",
                            "probes": [true, false, true]
                        },
                        {
                            "classname": "$testClassname",
                            "signature": "$testMethodSignature2",
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

        val savedCoverageMethod1 = MethodCoverageTable.selectAll().asSequence()
            .filter { it[MethodCoverageTable.groupId] == testGroup }
            .filter { it[MethodCoverageTable.appId] == testApp }
            .filter { it[MethodCoverageTable.instanceId] == testInstance }
            .filter { it[MethodCoverageTable.classname] == testClassname }
            .filter { it[MethodCoverageTable.signature] == testMethodSignature1 }
            .filter { it[MethodCoverageTable.testId] == testTestId }
            .toList()
        assertEquals(1, savedCoverageMethod1.size)
        savedCoverageMethod1.forEach {
            assertTrue(it[MethodCoverageTable.createdAt] >= timeBeforeTest)
        }

        val savedCoverageMethod2 = MethodCoverageTable.selectAll().asSequence()
            .filter { it[MethodCoverageTable.groupId] == testGroup }
            .filter { it[MethodCoverageTable.appId] == testApp }
            .filter { it[MethodCoverageTable.instanceId] == testInstance }
            .filter { it[MethodCoverageTable.classname] == testClassname }
            .filter { it[MethodCoverageTable.signature] == testMethodSignature2 }
            .filter { it[MethodCoverageTable.testId] == testTestId }
            .toList()
        assertEquals(1, savedCoverageMethod2.size)
        savedCoverageMethod2.forEach {
            assertTrue(it[MethodCoverageTable.createdAt] >= timeBeforeTest)
        }
    }
}