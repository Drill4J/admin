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
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchCoverageRequestRepository
import com.epam.drill.admin.writer.rawdata.table.TestLaunchCoverageRequestTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNullOrEmpty
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.upsert
class TestLaunchCoverageRequestRepositoryImpl : TestLaunchCoverageRequestRepository {
    override suspend fun upsert(groupId: String, testSessionId: String, testDefinitionId: String?) {
        TestLaunchCoverageRequestTable.upsert(
            TestLaunchCoverageRequestTable.groupId,
            TestLaunchCoverageRequestTable.testSessionId,
            TestLaunchCoverageRequestTable.testDefinitionId,
            onUpdateExclude = listOf(TestLaunchCoverageRequestTable.createdAt)
        ) {
            it[this.groupId] = groupId
            it[this.testSessionId] = testSessionId
            it[this.testDefinitionId] = testDefinitionId ?: ""
            it[this.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
        }
    }
    override suspend fun delete(groupId: String, testSessionId: String, testDefinitionId: String?) {
        TestLaunchCoverageRequestTable.deleteWhere {
            (TestLaunchCoverageRequestTable.groupId eq groupId) and
                (TestLaunchCoverageRequestTable.testSessionId eq testSessionId) and
                if (testDefinitionId != null) {
                    TestLaunchCoverageRequestTable.testDefinitionId eq testDefinitionId
                } else {
                    TestLaunchCoverageRequestTable.testDefinitionId.isNullOrEmpty()
                }
        }
    }
}
