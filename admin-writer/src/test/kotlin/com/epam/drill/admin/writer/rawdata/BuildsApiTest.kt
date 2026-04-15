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

import com.epam.drill.admin.writer.rawdata.route.putBuilds
import com.epam.drill.admin.writer.rawdata.route.putBuildsInfo
import com.epam.drill.admin.writer.rawdata.table.BuildTable
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildsApiTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {

    @Test
    fun `given new build, put builds service should save new build in database and return OK`() = withRollback {
        val testGroup = "test-group"
        val testApp = "test-app"
        val testBuildVersion = "1.0.0"
        val timeBeforeTest = LocalDateTime.now()
        val app = drillApplication(rawDataServicesDIModule) {
            putBuilds()
        }

        app.client.put("/builds") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                    "groupId": "$testGroup",
                    "appId": "$testApp",
                    "buildVersion": "$testBuildVersion",
                    "commitSha": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertJsonEquals(
                """
                {
                    "message": "Build saved"
                }
            """.trimIndent(), bodyAsText()
            )
        }

        val savedBuilds = BuildTable.selectAll()
            .filter { it[BuildTable.groupId] == testGroup }
            .filter { it[BuildTable.appId] == testApp }
            .filter { it[BuildTable.buildVersion] == testBuildVersion }
        assertEquals(1, savedBuilds.size)
        savedBuilds.forEach {
            assertNull(it[BuildTable.branch])
            assertNotNull(it[BuildTable.commitSha])
            assertNull(it[BuildTable.commitAuthor])
            assertNull(it[BuildTable.commitMessage])
            assertNull(it[BuildTable.committedAt])
            assertTrue(it[BuildTable.createdAt] >= timeBeforeTest)
        }
    }

    @Test
    fun `given new build, put builds info should create new build with info fields and return OK`() = withRollback {
        val testGroup = "test-group"
        val testApp = "test-app"
        val testBuildVersion = "2.0.0"
        val timeBeforeTest = LocalDateTime.now()
        val app = drillApplication(rawDataServicesDIModule) {
            putBuildsInfo()
        }

        app.client.put("/builds/info") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                    "groupId": "$testGroup",
                    "appId": "$testApp",
                    "buildVersion": "$testBuildVersion",
                    "commitSha": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                    "branch": "main",
                    "commitMessage": "Initial commit",
                    "commitDate": "Thu Feb 27 10:06:24 2025 +0100",
                    "commitAuthor": "John Doe"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertJsonEquals(
                """
                {
                    "message": "Build info saved"
                }
            """.trimIndent(), bodyAsText()
            )
        }

        val savedBuilds = BuildTable.selectAll()
            .filter { it[BuildTable.groupId] == testGroup }
            .filter { it[BuildTable.appId] == testApp }
            .filter { it[BuildTable.buildVersion] == testBuildVersion }
        assertEquals(1, savedBuilds.size)
        savedBuilds.forEach {
            assertEquals("main", it[BuildTable.branch])
            assertNotNull(it[BuildTable.commitSha])
            assertEquals("John Doe", it[BuildTable.commitAuthor])
            assertEquals("Initial commit", it[BuildTable.commitMessage])
            assertNotNull(it[BuildTable.committedAt])
            assertTrue(it[BuildTable.createdAt] >= timeBeforeTest)
        }
    }

    @Test
    fun `given existing build info, put builds should not update info fields and return OK`() = withRollback {
        val testGroup = "test-group"
        val testApp = "test-app"
        val testBuildVersion = "3.0.0"
        val testCommitSha = "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3"
        val app = drillApplication(rawDataServicesDIModule) {
            putBuildsInfo()
            putBuilds()
        }
        app.client.put("/builds/info") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                    "groupId": "$testGroup",
                    "appId": "$testApp",
                    "buildVersion": "$testBuildVersion",
                    "commitSha": "$testCommitSha",
                    "branch": "develop",
                    "commitMessage": "Feature commit",
                    "commitDate": "Thu Feb 27 10:06:24 2025 +0100",
                    "commitAuthor": "Jane Doe"
                }
                """.trimIndent()
            )
        }

        app.client.put("/builds") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                    "groupId": "$testGroup",
                    "appId": "$testApp",
                    "buildVersion": "$testBuildVersion",
                    "commitSha": "$testCommitSha"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        val buildsBeforeInfo = BuildTable.selectAll()
            .filter { it[BuildTable.groupId] == testGroup }
            .filter { it[BuildTable.appId] == testApp }
            .filter { it[BuildTable.buildVersion] == testBuildVersion }
        assertEquals(1, buildsBeforeInfo.size)
        buildsBeforeInfo.forEach {
            assertNotNull(it[BuildTable.branch])
            assertNotNull(it[BuildTable.commitAuthor])
            assertNotNull(it[BuildTable.commitMessage])
            assertNotNull(it[BuildTable.committedAt])
        }
    }

}