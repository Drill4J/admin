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

import com.epam.drill.admin.writer.rawdata.route.putMethods
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MethodsApiTest : DatabaseTests() {

    @Test
    fun `given new methods, put methods service should save new methods in database and return OK`() = withRollback {
        val testGroup = "test-group"
        val testApp = "test-app"
        val testInstance = "test-instance"
        val testBuildVersion = "1.0.0"
        val testClassname = "com.example.TestClass"
        val testMethod1 = "testMethod1"
        val testMethod2 = "testMethod2"
        val timeBeforeTest = LocalDateTime.now()
        val app = dataIngestApplication {
            routing {
                putMethods()
            }
        }

        app.client.put("/methods") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                    "groupId": "$testGroup",
                    "appId": "$testApp",
                    "instanceId": "$testInstance",
                    "commitSha": "test-commit-sha",
                    "buildVersion": "$testBuildVersion",
                    "methods": [
                        {
                            "classname": "$testClassname",
                            "name": "$testMethod1",
                            "params": "String",
                            "returnType": "void",
                            "probesCount": 1,
                            "probesStartPos": 0,
                            "bodyChecksum": "checksum-1"
                        },
                        {
                            "classname": "$testClassname",
                            "name": "$testMethod2",
                            "params": "String",
                            "returnType": "void",
                            "probesCount": 2,
                            "probesStartPos": 1,
                            "bodyChecksum": "checksum-2"
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
                    "message": "Methods saved"
                }
            """.trimIndent(), bodyAsText()
            )
        }

        val savedMethods = MethodTable.selectAll()
            .filter { it[MethodTable.groupId] == testGroup }
            .filter { it[MethodTable.appId] == testApp }
            .filter { it[MethodTable.classname] == testClassname }
        assertEquals(2, savedMethods.size)
        savedMethods.forEach {
            assertTrue(it[MethodTable.createdAt] >= timeBeforeTest)
        }
    }
}