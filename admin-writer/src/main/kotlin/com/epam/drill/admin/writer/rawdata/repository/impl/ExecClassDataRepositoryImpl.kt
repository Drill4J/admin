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

import com.epam.drill.admin.writer.rawdata.entity.RawCoverageData
import com.epam.drill.admin.writer.rawdata.repository.ExecClassDataRepository
import com.epam.drill.admin.writer.rawdata.table.ExecClassDataTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select

class ExecClassDataRepositoryImpl: ExecClassDataRepository {
    override fun createMany(data: List<RawCoverageData>) {
        ExecClassDataTable.batchInsert(data, shouldReturnGeneratedValues = false) {
            this[ExecClassDataTable.instanceId] = it.instanceId
            this[ExecClassDataTable.className] = it.className
            this[ExecClassDataTable.testId] = it.testId
            this[ExecClassDataTable.probes] = it.probes
        }
    }

    override fun findAllByInstanceIds(instanceIds: List<String>): List<RawCoverageData> {
        return ExecClassDataTable
            .select { ExecClassDataTable.instanceId inList instanceIds }
            .map { it.toRawCoverageData() }
    }

    // TODO classId and sessionId are omitted. Decide if they are required
    private fun ResultRow.toRawCoverageData() = RawCoverageData(
        instanceId = this[ExecClassDataTable.instanceId],
        className = this[ExecClassDataTable.className],
        testId = this[ExecClassDataTable.testId],
        probes = this[ExecClassDataTable.probes]
    )

}