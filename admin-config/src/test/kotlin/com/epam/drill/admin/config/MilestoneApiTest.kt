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
package com.epam.drill.admin.config

import com.epam.drill.admin.config.route.configRoutes
import com.epam.drill.admin.config.table.MilestoneAppTable
import com.epam.drill.admin.config.table.MilestoneQualityGateTable
import com.epam.drill.admin.config.table.MilestoneTable
import com.epam.drill.admin.config.table.ReleaseTrackAppTable
import com.epam.drill.admin.config.table.ReleaseTrackTable
import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.drillApplication
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

private const val GROUP = "test-group"
private const val TRACK = "main"
private const val MILESTONE = "release-1.0.0"
private const val APP_A = "checkout"
private const val APP_B = "inventory"
private const val BUILD_A = "$GROUP:$APP_A:1.0.0"
private const val BUILD_B = "$GROUP:$APP_B:2.0.0"

class MilestoneApiTest : DatabaseTests({ ConfigDatabaseConfig.init(it) }) {

    private lateinit var client: HttpClient

    @BeforeTest
    fun setUp() {
        client = drillApplication(configDIModule) {
            configRoutes()
        }.client
    }

    @AfterTest
    fun tearDown(): Unit = transaction {
        MilestoneAppTable.deleteAll()
        MilestoneQualityGateTable.deleteAll()
        MilestoneTable.deleteAll()
        ReleaseTrackAppTable.deleteAll()
        ReleaseTrackTable.deleteAll()
    }

    @Test
    fun `given track with apps, put milestone creates it with apps and returns OK`(): Unit = runBlocking {
        insertTrack(GROUP, TRACK)
        insertTrackApp(GROUP, TRACK, APP_A)
        insertTrackApp(GROUP, TRACK, APP_B)

        client.put("groups/$GROUP/release-tracks/$TRACK/milestones") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                  "milestoneId": "$MILESTONE",
                  "apps": [
                    {"appId": "$APP_A", "buildId": "$BUILD_A"},
                    {"appId": "$APP_B", "buildId": "$BUILD_B"}
                  ]
                }
            """.trimIndent())
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        transaction {
            assertEquals(
                1,
                MilestoneTable.selectAll()
                    .where { MilestoneTable.milestoneId eq MILESTONE }
                    .count()
            )
            assertEquals(
                2,
                MilestoneAppTable.selectAll()
                    .where { MilestoneAppTable.milestoneId eq MILESTONE }
                    .count()
            )
        }
    }

    @Test
    fun `given track without registered apps, put milestone with unknown appId returns 400`(): Unit = runBlocking {
        insertTrack(GROUP, TRACK)

        client.put("groups/$GROUP/release-tracks/$TRACK/milestones") {
            contentType(ContentType.Application.Json)
            setBody("""{"milestoneId": "$MILESTONE", "apps": [{"appId": "unknown", "buildId": "x"}]}""")
        }.apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `given existing milestone, second put replaces apps`(): Unit = runBlocking {
        insertTrack(GROUP, TRACK)
        insertTrackApp(GROUP, TRACK, APP_A)
        insertTrackApp(GROUP, TRACK, APP_B)

        client.put("groups/$GROUP/release-tracks/$TRACK/milestones") {
            contentType(ContentType.Application.Json)
            setBody("""{"milestoneId": "$MILESTONE", "apps": [{"appId": "$APP_A", "buildId": "$BUILD_A"}]}""")
        }
        client.put("groups/$GROUP/release-tracks/$TRACK/milestones") {
            contentType(ContentType.Application.Json)
            setBody("""{"milestoneId": "$MILESTONE", "apps": [{"appId": "$APP_B", "buildId": "$BUILD_B"}]}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        transaction {
            val apps = MilestoneAppTable.selectAll()
                .where { MilestoneAppTable.milestoneId eq MILESTONE }
                .map { it[MilestoneAppTable.appId] }
            assertEquals(listOf(APP_B), apps)
        }
    }

    @Test
    fun `given milestones in database, get returns list ordered by createdAt desc`(): Unit = runBlocking {
        insertTrack(GROUP, TRACK)
        insertMilestone(GROUP, TRACK, "v1")
        insertMilestone(GROUP, TRACK, "v2")

        val body = client.get("groups/$GROUP/release-tracks/$TRACK/milestones/list").bodyAsText()
        assertTrue(body.contains("v1"))
        assertTrue(body.contains("v2"))
    }

    @Test
    fun `given track and milestone, put overall quality gate targets saves them to database`(): Unit = runBlocking {
        insertTrack(GROUP, TRACK)
        insertMilestone(GROUP, TRACK, MILESTONE)

        client.put("groups/$GROUP/release-tracks/$TRACK/milestones/$MILESTONE/quality-gates/overall") {
            contentType(ContentType.Application.Json)
            setBody("""{"coverageTargetPercentage": 70.0, "methodsCoverageTargetPercentage": 65.0, "passedTestsTargetPercentage": 80.0}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        transaction {
            val row = MilestoneQualityGateTable.selectAll()
                .where { MilestoneQualityGateTable.milestoneId eq MILESTONE }
                .single()
            assertEquals(70.0, row[MilestoneQualityGateTable.overallCoverageTarget]?.toDouble())
            assertEquals(65.0, row[MilestoneQualityGateTable.overallMethodsCoverageTarget]?.toDouble())
            assertEquals(80.0, row[MilestoneQualityGateTable.overallPassedTestsTarget]?.toDouble())
        }
    }

    @Test
    fun `given existing overall targets, put impact targets does not overwrite overall targets`(): Unit = runBlocking {
        insertTrack(GROUP, TRACK)
        insertMilestone(GROUP, TRACK, MILESTONE)

        client.put("groups/$GROUP/release-tracks/$TRACK/milestones/$MILESTONE/quality-gates/overall") {
            contentType(ContentType.Application.Json)
            setBody("""{"coverageTargetPercentage": 70.0, "methodsCoverageTargetPercentage": 65.0, "passedTestsTargetPercentage": 80.0}""")
        }
        client.put("groups/$GROUP/release-tracks/$TRACK/milestones/$MILESTONE/quality-gates/impact") {
            contentType(ContentType.Application.Json)
            setBody("""{"coverageTargetPercentage": 75.0, "methodsCoverageTargetPercentage": 70.0, "passedTestsTargetPercentage": 85.0}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        transaction {
            val row = MilestoneQualityGateTable.selectAll()
                .where { MilestoneQualityGateTable.milestoneId eq MILESTONE }
                .single()
            assertEquals(70.0, row[MilestoneQualityGateTable.overallCoverageTarget]?.toDouble())
            assertEquals(75.0, row[MilestoneQualityGateTable.impactCoverageTarget]?.toDouble())
        }
    }

    private fun insertTrack(groupId: String, trackId: String) = transaction {
        ReleaseTrackTable.insert {
            it[this.groupId] = groupId
            it[this.trackId] = trackId
        }
    }

    private fun insertTrackApp(groupId: String, trackId: String, appId: String) = transaction {
        ReleaseTrackAppTable.insert {
            it[this.groupId] = groupId
            it[this.trackId] = trackId
            it[this.appId] = appId
        }
    }

    private fun insertMilestone(groupId: String, trackId: String, milestoneId: String) = transaction {
        MilestoneTable.insert {
            it[this.groupId] = groupId
            it[this.trackId] = trackId
            it[this.milestoneId] = milestoneId
        }
    }
}
