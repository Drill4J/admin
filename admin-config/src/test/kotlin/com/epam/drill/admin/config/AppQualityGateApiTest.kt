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
import com.epam.drill.admin.config.table.AppQualityGatesTable
import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.assertJsonEquals
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val GROUP = "test-group"
private const val APP = "checkout"

class AppQualityGateApiTest : DatabaseTests({ ConfigDatabaseConfig.init(it) }) {

    private lateinit var client: HttpClient

    @BeforeTest
    fun setUp() {
        client = drillApplication(configDIModule) {
            configRoutes()
        }.client
    }

    @AfterTest
    fun tearDown(): Unit = transaction {
        AppQualityGatesTable.deleteAll()
    }

    @Test
    fun `given saved overall targets, put and get overall roundtrip works`(): Unit = runBlocking {
        client.put("groups/$GROUP/apps/$APP/quality-gates/overall") {
            contentType(ContentType.Application.Json)
            setBody("""{"codeCoveragePercentage": 70.0, "methodCoveragePercentage": 65.0, "testsPassedPercentage": 80.0}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        val body = client.get("groups/$GROUP/apps/$APP/quality-gates/overall").bodyAsText()
        assertJsonEquals(
            """{"data": {"codeCoveragePercentage": 70.0, "methodCoveragePercentage": 65.0, "testsPassedPercentage": 80.0}}""",
            body
        )
    }

    @Test
    fun `given saved overall targets, put with null field clears that target`(): Unit = runBlocking {
        client.put("groups/$GROUP/apps/$APP/quality-gates/overall") {
            contentType(ContentType.Application.Json)
            setBody("""{"codeCoveragePercentage": 70.0, "methodCoveragePercentage": 65.0, "testsPassedPercentage": 80.0}""")
        }
        client.put("groups/$GROUP/apps/$APP/quality-gates/overall") {
            contentType(ContentType.Application.Json)
            setBody("""{"codeCoveragePercentage": 70.0, "methodCoveragePercentage": null, "testsPassedPercentage": 80.0}""")
        }

        val body = client.get("groups/$GROUP/apps/$APP/quality-gates/overall").bodyAsText()
        assertJsonEquals(
            """{"data": {"codeCoveragePercentage": 70.0, "methodCoveragePercentage": null, "testsPassedPercentage": 80.0}}""",
            body
        )
    }

    @Test
    fun `given saved overall targets, put impact targets does not overwrite overall`(): Unit = runBlocking {
        client.put("groups/$GROUP/apps/$APP/quality-gates/overall") {
            contentType(ContentType.Application.Json)
            setBody("""{"codeCoveragePercentage": 70.0, "methodCoveragePercentage": 65.0, "testsPassedPercentage": 80.0}""")
        }
        client.put("groups/$GROUP/apps/$APP/quality-gates/impact") {
            contentType(ContentType.Application.Json)
            setBody("""{"changeCoveragePercentage": 75.0, "changeMethodsCoveragePercentage": 70.0, "impactedTestsPassedPercentage": 85.0}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        transaction {
            val row = AppQualityGatesTable.selectAll()
                .where { (AppQualityGatesTable.groupId eq GROUP) and (AppQualityGatesTable.appId eq APP) }
                .single()
            assertEquals(70.0, row[AppQualityGatesTable.codeCoverageTarget]?.toDouble())
            assertEquals(65.0, row[AppQualityGatesTable.methodCoverageTarget]?.toDouble())
            assertEquals(75.0, row[AppQualityGatesTable.changeCoverageTarget]?.toDouble())
            assertEquals(70.0, row[AppQualityGatesTable.changeMethodsCoverageTarget]?.toDouble())
        }
    }

    @Test
    fun `given saved impact targets, put overall targets does not overwrite impact`(): Unit = runBlocking {
        client.put("groups/$GROUP/apps/$APP/quality-gates/impact") {
            contentType(ContentType.Application.Json)
            setBody("""{"changeCoveragePercentage": 75.0, "changeMethodsCoveragePercentage": 70.0, "impactedTestsPassedPercentage": 85.0}""")
        }
        client.put("groups/$GROUP/apps/$APP/quality-gates/overall") {
            contentType(ContentType.Application.Json)
            setBody("""{"codeCoveragePercentage": 70.0, "methodCoveragePercentage": 65.0, "testsPassedPercentage": 80.0}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        transaction {
            val row = AppQualityGatesTable.selectAll()
                .where { (AppQualityGatesTable.groupId eq GROUP) and (AppQualityGatesTable.appId eq APP) }
                .single()
            assertEquals(70.0, row[AppQualityGatesTable.codeCoverageTarget]?.toDouble())
            assertEquals(75.0, row[AppQualityGatesTable.changeCoverageTarget]?.toDouble())
        }
    }

    @Test
    fun `given saved impact targets, put and get impact roundtrip works`(): Unit = runBlocking {
        client.put("groups/$GROUP/apps/$APP/quality-gates/impact") {
            contentType(ContentType.Application.Json)
            setBody("""{"changeCoveragePercentage": 75.0, "changeMethodsCoveragePercentage": 70.0, "impactedTestsPassedPercentage": 85.0}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        val body = client.get("groups/$GROUP/apps/$APP/quality-gates/impact").bodyAsText()
        assertJsonEquals(
            """{"data": {"changeCoveragePercentage": 75.0, "changeMethodsCoveragePercentage": 70.0, "impactedTestsPassedPercentage": 85.0}}""",
            body
        )
    }
}
