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

import com.epam.drill.admin.writer.rawdata.entity.TestDefinition
import com.epam.drill.admin.writer.rawdata.repository.TestDefinitionRepository
import com.epam.drill.admin.writer.rawdata.table.TestDefinitionTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import java.time.LocalDate

class TestDefinitionRepositoryImpl: TestDefinitionRepository {
    override suspend fun createMany(testDefinitionList: List<TestDefinition>) {
        TestDefinitionTable.batchUpsert(
            testDefinitionList,
            onUpdateExclude = listOf(TestDefinitionTable.createdAt),
            shouldReturnGeneratedValues = false
        ) {
            this[TestDefinitionTable.id] = it.id
            this[TestDefinitionTable.groupId] = it.groupId
            this[TestDefinitionTable.type] = it.type
            this[TestDefinitionTable.runner] = it.runner
            this[TestDefinitionTable.name] = it.name
            this[TestDefinitionTable.path] = it.path
            this[TestDefinitionTable.metadata] = it.metadata
            this[TestDefinitionTable.tags] = it.tags
            this[TestDefinitionTable.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
        }
    }


    override suspend fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        TestDefinitionTable.deleteWhere { (TestDefinitionTable.groupId eq groupId) and (TestDefinitionTable.updatedAt less createdBefore.atStartOfDay()) }
    }
}
