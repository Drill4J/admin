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
import com.epam.drill.admin.test.drillApplication
import com.epam.drill.admin.test.withRollback
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchCoverageRequestRepository
import com.epam.drill.admin.writer.rawdata.repository.impl.TestLaunchCoverageRequestRepositoryImpl
import com.epam.drill.admin.writer.rawdata.route.deleteTestLaunchCoverageRequest
import com.epam.drill.admin.writer.rawdata.route.deleteTestSessionCoverageRequest
import com.epam.drill.admin.writer.rawdata.route.putTestLaunchCoverageRequest
import com.epam.drill.admin.writer.rawdata.route.putTestSessionCoverageRequest
import com.epam.drill.admin.writer.rawdata.service.DataManagementService
import com.epam.drill.admin.writer.rawdata.service.impl.DataManagementServiceImpl
import com.epam.drill.admin.writer.rawdata.table.TestLaunchCoverageRequestTable
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.selectAll
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.mockito.kotlin.mock
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLaunchCoverageRequestApiTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {

    @Test
    fun `put coverage request should create record in database and return OK`() = withRollback {
        val groupId = "test-group"
        val testSessionId = "session-1"
        val testDefinitionId = "definition-1"
        val timeBeforeTest = LocalDateTime.now()
        val app = drillApplication(testDIModule) {
            putTestLaunchCoverageRequest()
        }
        app.client.put("/groups/$groupId/tests/sessions/$testSessionId/definitions/$testDefinitionId/requests")
            .apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        val records = TestLaunchCoverageRequestTable.selectAll()
            .filter { it[TestLaunchCoverageRequestTable.groupId] == groupId }
            .filter { it[TestLaunchCoverageRequestTable.testSessionId] == testSessionId }
            .filter { it[TestLaunchCoverageRequestTable.testDefinitionId] == testDefinitionId }
        assertEquals(1, records.size)
        assertTrue(records.first()[TestLaunchCoverageRequestTable.createdAt] >= timeBeforeTest)
        assertTrue(records.first()[TestLaunchCoverageRequestTable.updatedAt] >= timeBeforeTest)
    }

    @Test
    fun `put coverage request twice should upsert record (idempotent) and return OK`() = withRollback {
        val groupId = "test-group"
        val testSessionId = "session-2"
        val testDefinitionId = "definition-2"
        val timeBeforeCreate = LocalDateTime.now()
        val app = drillApplication(testDIModule) {
            putTestLaunchCoverageRequest()
        }
        val url = "/groups/$groupId/tests/sessions/$testSessionId/definitions/$testDefinitionId/requests"
        app.client.put(url).apply { assertEquals(HttpStatusCode.OK, status) }
        val timeBeforeUpdate = LocalDateTime.now()
        app.client.put(url).apply { assertEquals(HttpStatusCode.OK, status) }
        val records = TestLaunchCoverageRequestTable.selectAll()
            .filter { it[TestLaunchCoverageRequestTable.groupId] == groupId }
            .filter { it[TestLaunchCoverageRequestTable.testSessionId] == testSessionId }
            .filter { it[TestLaunchCoverageRequestTable.testDefinitionId] == testDefinitionId }
        assertEquals(1, records.size, "Upsert should result in exactly one record")
        assertTrue(records.first()[TestLaunchCoverageRequestTable.createdAt] >= timeBeforeCreate)
        assertTrue(records.first()[TestLaunchCoverageRequestTable.updatedAt] >= timeBeforeUpdate)
    }

    @Test
    fun `delete coverage request should remove record from database and return OK`() = withRollback {
        val groupId = "test-group"
        val testSessionId = "session-3"
        val testDefinitionId = "definition-3"
        val url = "/groups/$groupId/tests/sessions/$testSessionId/definitions/$testDefinitionId/requests"
        val app = drillApplication(testDIModule) {
            putTestLaunchCoverageRequest()
            deleteTestLaunchCoverageRequest()
        }
        app.client.put(url).apply { assertEquals(HttpStatusCode.OK, status) }
        val recordsAfterPut = TestLaunchCoverageRequestTable.selectAll()
            .filter { it[TestLaunchCoverageRequestTable.groupId] == groupId }
            .filter { it[TestLaunchCoverageRequestTable.testSessionId] == testSessionId }
            .filter { it[TestLaunchCoverageRequestTable.testDefinitionId] == testDefinitionId }
        assertEquals(1, recordsAfterPut.size, "Record should exist after PUT")
        app.client.delete(url).apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        val recordsAfterDelete = TestLaunchCoverageRequestTable.selectAll()
            .filter { it[TestLaunchCoverageRequestTable.groupId] == groupId }
            .filter { it[TestLaunchCoverageRequestTable.testSessionId] == testSessionId }
            .filter { it[TestLaunchCoverageRequestTable.testDefinitionId] == testDefinitionId }
        assertEquals(0, recordsAfterDelete.size, "Record should be removed after DELETE")
    }

    @Test
    fun `put session coverage request should create record with null testDefinitionId and return OK`() = withRollback {
        val groupId = "test-group"
        val testSessionId = "session-1"
        val timeBeforeTest = LocalDateTime.now()
        val app = drillApplication(testDIModule) {
            putTestSessionCoverageRequest()
        }
        app.client.put("/groups/$groupId/tests/sessions/$testSessionId/requests")
            .apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        val records = TestLaunchCoverageRequestTable.selectAll()
            .filter { it[TestLaunchCoverageRequestTable.groupId] == groupId }
            .filter { it[TestLaunchCoverageRequestTable.testSessionId] == testSessionId }
            .filter { it[TestLaunchCoverageRequestTable.testDefinitionId].isNullOrEmpty() }
        assertEquals(1, records.size)
        assertTrue(records.first()[TestLaunchCoverageRequestTable.createdAt] >= timeBeforeTest)
        assertTrue(records.first()[TestLaunchCoverageRequestTable.updatedAt] >= timeBeforeTest)
    }

    @Test
    fun `put session coverage request twice should upsert record (idempotent) and return OK`() = withRollback {
        val groupId = "test-group"
        val testSessionId = "session-2"
        val app = drillApplication(testDIModule) {
            putTestSessionCoverageRequest()
        }
        val url = "/groups/$groupId/tests/sessions/$testSessionId/requests"
        app.client.put(url).apply { assertEquals(HttpStatusCode.OK, status) }
        app.client.put(url).apply { assertEquals(HttpStatusCode.OK, status) }
        val records = TestLaunchCoverageRequestTable.selectAll()
            .filter { it[TestLaunchCoverageRequestTable.groupId] == groupId }
            .filter { it[TestLaunchCoverageRequestTable.testSessionId] == testSessionId }
            .filter { it[TestLaunchCoverageRequestTable.testDefinitionId].isNullOrEmpty() }
        assertEquals(1, records.size, "Upsert should result in exactly one record")
    }

    @Test
    fun `delete session coverage request should remove record from database and return OK`() = withRollback {
        val groupId = "test-group"
        val testSessionId = "session-3"
        val url = "/groups/$groupId/tests/sessions/$testSessionId/requests"
        val app = drillApplication(testDIModule) {
            putTestSessionCoverageRequest()
            deleteTestSessionCoverageRequest()
        }
        app.client.put(url).apply { assertEquals(HttpStatusCode.OK, status) }
        val recordsAfterPut = TestLaunchCoverageRequestTable.selectAll()
            .filter { it[TestLaunchCoverageRequestTable.groupId] == groupId }
            .filter { it[TestLaunchCoverageRequestTable.testSessionId] == testSessionId }
            .filter { it[TestLaunchCoverageRequestTable.testDefinitionId].isNullOrEmpty() }
        assertEquals(1, recordsAfterPut.size, "Record should exist after PUT")
        app.client.delete(url).apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        val recordsAfterDelete = TestLaunchCoverageRequestTable.selectAll()
            .filter { it[TestLaunchCoverageRequestTable.groupId] == groupId }
            .filter { it[TestLaunchCoverageRequestTable.testSessionId] == testSessionId }
            .filter { it[TestLaunchCoverageRequestTable.testDefinitionId].isNullOrEmpty() }
        assertEquals(0, recordsAfterDelete.size, "Record should be removed after DELETE")
    }

    private val testDIModule = DI.Module("testLaunchCoverageRequestModule") {
        bind<TestLaunchCoverageRequestRepository>() with singleton { TestLaunchCoverageRequestRepositoryImpl() }
        bind<DataManagementService>() with singleton {
            DataManagementServiceImpl(
                testLaunchCoverageRequestRepository = instance(),
                buildRepository = mock(),
                testSessionRepository = mock(),
                coverageRepository = mock(),
                instanceRepository = mock(),
                methodRepository = mock(),
                testSessionBuildRepository = mock(),
                testLaunchRepository = mock(),
                methodIgnoreRuleRepository = mock(),
                scheduler = mock(),
            )
        }
    }
}
