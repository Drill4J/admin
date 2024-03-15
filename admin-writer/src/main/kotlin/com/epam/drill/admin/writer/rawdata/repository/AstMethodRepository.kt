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
package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.AstEntityData
import com.epam.drill.admin.writer.rawdata.table.AstMethodTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select

object AstMethodRepository {
    fun createMany(data: List<AstEntityData>) {
        AstMethodTable.batchInsert(data, shouldReturnGeneratedValues = false) {
            this[AstMethodTable.instanceId] = it.instanceId
            this[AstMethodTable.className] = it.className
            this[AstMethodTable.name] = it.name
            this[AstMethodTable.params] = it.params
            this[AstMethodTable.returnType] = it.returnType
            this[AstMethodTable.probesStartPos] = it.probesStartPos
            this[AstMethodTable.bodyChecksum] = it.bodyChecksum
            this[AstMethodTable.probesCount] = it.probesCount
        }
    }

    fun findAllByInstanceIds(instanceIds: List<String>): List<AstEntityData> {
        return AstMethodTable
            .select { AstMethodTable.instanceId inList instanceIds }
            .distinctBy { row ->
                Pair(row[AstMethodTable.className], row[AstMethodTable.name])
            }
            .map { it.toAstEntityData() }
    }

    private fun ResultRow.toAstEntityData() = AstEntityData(
        instanceId = this[AstMethodTable.instanceId],
        className = this[AstMethodTable.className],
        name = this[AstMethodTable.name],
        params = this[AstMethodTable.params],
        returnType = this[AstMethodTable.returnType],
        probesCount = this[AstMethodTable.probesCount],
        probesStartPos = this[AstMethodTable.probesStartPos],
        bodyChecksum = this[AstMethodTable.bodyChecksum]
    )
}