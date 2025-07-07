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
import com.epam.drill.admin.writer.rawdata.table.CoverageTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import java.time.LocalDate

class CoverageRepositoryImpl: CoverageRepository {
    override fun createMany(data: List<Coverage>) {
        CoverageTable.batchInsert(data, shouldReturnGeneratedValues = false) {
            this[CoverageTable.groupId] = it.groupId
            this[CoverageTable.appId] = it.appId
            this[CoverageTable.instanceId] = it.instanceId
            this[CoverageTable.classname] = it.classname
            this[CoverageTable.testId] = it.testId
            this[CoverageTable.testSessionId] = it.testSessionId
            this[CoverageTable.probes] = it.probes
        }
    }

    override fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        CoverageTable.deleteWhere { (CoverageTable.groupId eq groupId) and (CoverageTable.createdAt less createdBefore.atStartOfDay()) }
    }
}