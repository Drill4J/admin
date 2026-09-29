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
package com.epam.drill.admin.writer.rawdata.repository.impl

import com.epam.drill.admin.writer.rawdata.entity.TestSession
import com.epam.drill.admin.writer.rawdata.entity.TestSessionHeartbeat
import com.epam.drill.admin.writer.rawdata.repository.TestSessionRepository
import com.epam.drill.admin.writer.rawdata.route.payload.TestSessionStatus
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate

class TestSessionRepositoryImpl : TestSessionRepository {

    override suspend fun existsById(groupId: String, testSessionId: String): Boolean {
        return TestSessionTable.selectAll().where {
            (TestSessionTable.groupId eq groupId) and
                    (TestSessionTable.id eq testSessionId)
        }.any()
    }

    override suspend fun create(session: TestSession) {
        TestSessionTable.upsert(
            onUpdateExclude = listOf(
                TestSessionTable.status,
                TestSessionTable.lastHeartbeatAt,
            )
        ) {
            it[id] = session.id
            it[groupId] = session.groupId
            it[testProjectId] = session.testProjectId
            it[testTaskId] = session.testTaskId
            it[startedAt] = session.startedAt
            it[createdBy] = session.createdBy
            it[lastHeartbeatAt] = CurrentDateTime
            it[status] = TestSessionStatus.RUNNING.name
        }
    }

    override suspend fun updateHeartbeat(session: TestSessionHeartbeat) {
        TestSessionTable.update(where = {
            (TestSessionTable.groupId eq session.groupId) and
                    (TestSessionTable.testProjectId eq session.testProjectId) and
                    (TestSessionTable.id eq session.id)
        }) {
            it[lastHeartbeatAt] = CurrentDateTime
            it[status] = session.status.name
        }
    }

    override suspend fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        TestSessionTable.deleteWhere { (TestSessionTable.groupId eq groupId) and (TestSessionTable.createdAt less createdBefore.atStartOfDay()) }
    }

    override suspend fun existsByGroupIdAndTestProjectId(groupId: String, testProjectId: String): Boolean {
        return TestSessionTable.selectAll().where {
            (TestSessionTable.groupId eq groupId) and
                    (TestSessionTable.testProjectId eq testProjectId)
        }.any()
    }

    override suspend fun deleteByTestSessionId(groupId: String, testProjectId: String?, testSessionId: String) {
        TestSessionTable.deleteWhere {
            (TestSessionTable.groupId eq groupId) and
                    (TestSessionTable.id eq testSessionId) and
                    (testProjectId?.let { TestSessionTable.testProjectId eq it } ?: Op.TRUE)
        }
    }

    override suspend fun deleteAllByTestProjectId(groupId: String, testProjectId: String) {
        TestSessionTable.deleteWhere {
            (TestSessionTable.groupId eq groupId) and (TestSessionTable.testProjectId eq testProjectId)
        }
    }

    override suspend fun deleteAllByGroupId(groupId: String) {
        TestSessionTable.deleteWhere { TestSessionTable.groupId eq groupId }
    }
}