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
import com.epam.drill.admin.config.table.ReleaseTrackTable
import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.assertJsonEquals
import com.epam.drill.admin.test.drillApplication
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val GROUP = "test-group"
private const val TRACK = "main"

class ReleaseTrackApiTest : DatabaseTests({ ConfigDatabaseConfig.init(it) }) {

    private lateinit var client: HttpClient

    @BeforeTest
    fun setUp() {
        client = drillApplication(configDIModule) {
            configRoutes()
        }.client
    }

    @AfterTest
    fun tearDown(): Unit = transaction {
        ReleaseTrackTable.deleteAll()
    }

    @Test
    fun `given new track, post creates it in database and returns OK`(): Unit = runBlocking {
        client.post("groups/$GROUP/release-tracks") {
            contentType(ContentType.Application.Json)
            setBody("""{"trackId": "$TRACK"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        transaction {
            assertEquals(1, ReleaseTrackTable.selectAll()
                .where { ReleaseTrackTable.groupId eq GROUP }.count())
            val row = ReleaseTrackTable.selectAll()
                .where { ReleaseTrackTable.groupId eq GROUP }
                .single()
            assertEquals(TRACK, row[ReleaseTrackTable.trackId])
        }
    }

    @Test
    fun `given existing track, post with same trackId returns 409 Conflict`(): Unit = runBlocking {
        client.post("groups/$GROUP/release-tracks") {
            contentType(ContentType.Application.Json)
            setBody("""{"trackId": "$TRACK"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.post("groups/$GROUP/release-tracks") {
            contentType(ContentType.Application.Json)
            setBody("""{"trackId": "$TRACK"}""")
        }.apply {
            assertEquals(HttpStatusCode.Conflict, status)
        }
    }

    @Test
    fun `given existing track, post with same trackId in different case returns 409 Conflict`(): Unit = runBlocking {
        client.post("groups/$GROUP/release-tracks") {
            contentType(ContentType.Application.Json)
            setBody("""{"trackId": "$TRACK"}""")
        }

        client.post("groups/$GROUP/release-tracks") {
            contentType(ContentType.Application.Json)
            setBody("""{"trackId": "${TRACK.uppercase()}"}""")
        }.apply {
            assertEquals(HttpStatusCode.Conflict, status)
        }
    }

    @Test
    fun `given track in database, get returns it with milestoneCount`(): Unit = runBlocking {
        client.post("groups/$GROUP/release-tracks") {
            contentType(ContentType.Application.Json)
            setBody("""{"trackId": "$TRACK"}""")
        }

        val body = client.get("groups/$GROUP/release-tracks").bodyAsText()
        assertTrue(body.contains(TRACK))
        assertTrue(body.contains("milestoneCount"))
    }

    @Test
    fun `given no tracks, get returns empty list`(): Unit = runBlocking {
        val body = client.get("groups/$GROUP/release-tracks").bodyAsText()
        assertJsonEquals("""{"data": []}""", body)
    }
}