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
import com.epam.drill.admin.writer.rawdata.repository.TestSessionRepository
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate

class TestSessionRepositoryImpl : TestSessionRepository {
    override fun create(session: TestSession) {
        TestSessionTable.upsert {
            it[id] = session.id
            it[groupId] = session.groupId
            it[testTaskId] = session.testTaskId
            it[startedAt] = session.startedAt
            it[createdBy] = session.createdBy
        }
    }

    override fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        TestSessionTable.deleteWhere { (TestSessionTable.groupId eq groupId) and (TestSessionTable.createdAt less createdBefore.atStartOfDay()) }
    }
}