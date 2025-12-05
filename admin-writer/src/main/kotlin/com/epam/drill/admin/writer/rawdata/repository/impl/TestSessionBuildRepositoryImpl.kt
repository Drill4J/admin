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

import com.epam.drill.admin.writer.rawdata.repository.TestSessionBuildRepository
import com.epam.drill.admin.writer.rawdata.table.TestSessionBuildTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import java.time.LocalDate

class TestSessionBuildRepositoryImpl : TestSessionBuildRepository {
    override suspend fun create(testSessionId: String, buildId: String, groupId: String) {
        TestSessionBuildTable.insert {
            it[TestSessionBuildTable.testSessionId] = testSessionId
            it[TestSessionBuildTable.buildId] = buildId
            it[TestSessionBuildTable.groupId] = groupId
        }
    }

    override suspend fun deleteAllByTestSessionId(testSessionId: String) {
        TestSessionBuildTable.deleteWhere { TestSessionBuildTable.testSessionId eq testSessionId }
    }

    override suspend fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        TestSessionBuildTable.deleteWhere {
            (TestSessionBuildTable.groupId eq groupId) and
                    (TestSessionBuildTable.createdAt less createdBefore.atStartOfDay())
        }
    }

    override suspend fun deleteAllByBuildId(groupId: String, appId: String, buildId: String) {
        TestSessionBuildTable.deleteWhere {
            (TestSessionBuildTable.groupId eq groupId) and
            (TestSessionBuildTable.buildId eq buildId)
        }
    }

    override suspend fun deleteAllByTestSessionId(groupId: String, testSessionId: String) {
        TestSessionBuildTable.deleteWhere {
            (TestSessionBuildTable.groupId eq groupId) and
            (TestSessionBuildTable.testSessionId eq testSessionId)
        }
    }
}