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

import com.epam.drill.admin.writer.rawdata.route.putInstances
import com.epam.drill.admin.writer.rawdata.table.BuildTable
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.test.*
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstancesApiTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {
    private val testExistingGroup = "test-old-group"
    private val testExistingApp = "test-old-app"
    private val testExistingBuildVersion = "0.0.1"

    @BeforeEach
    fun setUp(): Unit = transaction {
        BuildTable.insert {
            it[id] = "$testExistingGroup:$testExistingApp:$testExistingBuildVersion"
            it[groupId] = testExistingGroup
            it[appId] = testExistingApp
            it[buildVersion] = testExistingBuildVersion
        }
    }

    @AfterEach
    fun tearDown(): Unit = transaction {
        BuildTable.deleteWhere {
            BuildTable.id eq "$testExistingGroup:$testExistingApp:$testExistingBuildVersion"
        }
    }

    @Test
    fun `given non-existent build, put instances service should save new instance and new build in database and return OK`() =
        withRollback {
            val testGroup = "test-group"
            val testApp = "test-app"
            val testInstance = "test-instance"
            val timeBeforeTest = LocalDateTime.now()
            val app = drillApplication(rawDataServicesDIModule) {
                putInstances()
            }

            app.client.put("/instances") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    """
                {
                    "groupId": "$testGroup",
                    "appId": "$testApp",
                    "instanceId": "$testInstance",
                    "commitSha": "123456",
                    "buildVersion": "1.0.0",
                    "envId": "test-env"                    
                }
                """.trimIndent()
                )
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
                assertJsonEquals(
                    """
                {
                    "message": "Instance saved"
                }
            """.trimIndent(), bodyAsText()
                )
            }

            val savedInstances = InstanceTable.selectAll()
                .filter { it[InstanceTable.groupId] == testGroup }
                .filter { it[InstanceTable.appId] == testApp }
                .filter { it[InstanceTable.id].value == testInstance }
            assertEquals(1, savedInstances.size)
            savedInstances.forEach {
                assertNotNull(it[InstanceTable.envId])
                assertTrue(it[InstanceTable.createdAt] >= timeBeforeTest)
            }
            val savedBuilds = BuildTable.selectAll()
                .filter { it[BuildTable.groupId] == testGroup }
                .filter { it[BuildTable.appId] == testApp }
                .filter { it[BuildTable.instanceId] == testInstance }
            assertEquals(1, savedBuilds.size)
            savedBuilds.forEach {
                assertNotNull(it[BuildTable.commitSha])
                assertNotNull(it[BuildTable.buildVersion])
                assertTrue(it[BuildTable.createdAt] >= timeBeforeTest)
            }
        }

    @Test
    fun `given existing build, put instances service should refer to existing build, save new instance in database and return OK`() =
        withRollback {
            val testInstance = "test-instance"
            val timeBeforeTest = LocalDateTime.now()
            val app = drillApplication(rawDataServicesDIModule) {
                putInstances()
            }

            app.client.put("/instances") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    """
                {
                    "groupId": "$testExistingGroup",
                    "appId": "$testExistingApp",
                    "buildVersion": "$testExistingBuildVersion",
                    "instanceId": "$testInstance",
                    "envId": "test-env"
                }
                """.trimIndent()
                )
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
                assertJsonEquals(
                    """
                {
                    "message": "Instance saved"
                }
            """.trimIndent(), bodyAsText()
                )
            }

            val savedInstances = InstanceTable.selectAll()
                .filter { it[InstanceTable.groupId] == testExistingGroup }
                .filter { it[InstanceTable.appId] == testExistingApp }
                .filter { it[InstanceTable.id].value == testInstance }
            assertEquals(1, savedInstances.size)
            savedInstances.forEach {
                assertNotNull(it[InstanceTable.envId])
                assertTrue(it[InstanceTable.createdAt] >= timeBeforeTest)
            }
            val savedBuilds = BuildTable.selectAll()
                .filter { it[BuildTable.groupId] == testExistingGroup }
                .filter { it[BuildTable.appId] == testExistingApp }
                .filter { it[BuildTable.buildVersion] == testExistingBuildVersion }
            assertEquals(1, savedBuilds.size)
        }
}