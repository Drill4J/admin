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

import com.epam.drill.admin.writer.rawdata.entity.TestLaunch
import com.epam.drill.admin.writer.rawdata.repository.TestLaunchRepository
import com.epam.drill.admin.writer.rawdata.table.TestLaunchTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import java.time.LocalDate

class TestLaunchRepositoryImpl: TestLaunchRepository {
    override fun createMany(testLaunchList: List<TestLaunch>) {
        TestLaunchTable.batchUpsert(testLaunchList, shouldReturnGeneratedValues = false) {
            this[TestLaunchTable.id] = it.id
            this[TestLaunchTable.groupId] = it.groupId
            this[TestLaunchTable.testDefinitionId] = it.testDefinitionId
            this[TestLaunchTable.testSessionId] = it.testSessionId
            this[TestLaunchTable.result] = it.result
        }
    }

    override fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        TestLaunchTable.deleteWhere { (TestLaunchTable.groupId eq groupId) and (TestLaunchTable.createdAt less createdBefore.atStartOfDay()) }
    }
}