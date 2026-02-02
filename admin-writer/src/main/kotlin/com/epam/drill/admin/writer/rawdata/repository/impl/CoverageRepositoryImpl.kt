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

import com.epam.drill.admin.writer.rawdata.entity.Coverage
import com.epam.drill.admin.writer.rawdata.repository.CoverageRepository
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import com.epam.drill.admin.writer.rawdata.table.MethodCoverageTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import java.time.LocalDate

class CoverageRepositoryImpl : CoverageRepository {
    override suspend fun createMany(data: List<Coverage>) {
        MethodCoverageTable.batchInsert(data, shouldReturnGeneratedValues = false) {
            this[MethodCoverageTable.groupId] = it.groupId
            this[MethodCoverageTable.appId] = it.appId
            this[MethodCoverageTable.instanceId] = it.instanceId
            this[MethodCoverageTable.buildId] = it.buildId
            this[MethodCoverageTable.signature] = it.signature
            this[MethodCoverageTable.testId] = it.testId
            this[MethodCoverageTable.testSessionId] = it.testSessionId
            this[MethodCoverageTable.probes] = it.probes
            this[MethodCoverageTable.probesCount] = it.probes.size
        }
    }

    override suspend fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        MethodCoverageTable.deleteWhere { (MethodCoverageTable.groupId eq groupId) and (MethodCoverageTable.createdAt less createdBefore.atStartOfDay()) }
    }

    override suspend fun deleteAllByBuildId(groupId: String, appId: String, buildId: String) {
        MethodCoverageTable.deleteWhere {
            (MethodCoverageTable.groupId eq groupId) and
            (MethodCoverageTable.appId eq appId) and
            (MethodCoverageTable.instanceId inSubQuery InstanceTable
                    .select(InstanceTable.id)
                    .where {
                        (InstanceTable.groupId eq groupId) and
                        (InstanceTable.appId eq appId) and
                        (InstanceTable.buildId eq buildId)
                    })
        }
    }

    override suspend fun deleteAllByTestSessionId(groupId: String, testSessionId: String) {
        MethodCoverageTable.deleteWhere {
            (MethodCoverageTable.groupId eq groupId) and
            (MethodCoverageTable.testSessionId eq testSessionId)
        }
    }
}