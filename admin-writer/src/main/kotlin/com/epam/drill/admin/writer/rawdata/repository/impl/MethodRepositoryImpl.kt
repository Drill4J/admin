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

import com.epam.drill.admin.writer.rawdata.entity.Method
import com.epam.drill.admin.writer.rawdata.repository.MethodRepository
import com.epam.drill.admin.writer.rawdata.table.MethodTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import java.time.LocalDate

class MethodRepositoryImpl : MethodRepository {
    override suspend fun createMany(data: List<Method>) {
        MethodTable.batchUpsert(
            data,
            shouldReturnGeneratedValues = false,
            onUpdateExclude = listOf(MethodTable.probesStartPos, MethodTable.bodyChecksum, MethodTable.probesCount)
        ) {
            this[MethodTable.id] = it.id
            this[MethodTable.groupId] = it.groupId
            this[MethodTable.appId] = it.appId
            this[MethodTable.buildId] = it.buildId
            this[MethodTable.classname] = it.classname
            this[MethodTable.name] = it.name
            this[MethodTable.params] = it.params
            this[MethodTable.returnType] = it.returnType
            this[MethodTable.probesStartPos] = it.probesStartPos
            this[MethodTable.bodyChecksum] = it.bodyChecksum
            this[MethodTable.signature] = it.signature
            this[MethodTable.probesCount] = it.probesCount
            it.annotations?.let { annotations ->
                this[MethodTable.annotations] = annotations.takeIf { it.isNotEmpty() }?.toString()
            }
            it.classAnnotations?.let { classAnnotations ->
                this[MethodTable.classAnnotations] = classAnnotations.takeIf { it.isNotEmpty() }?.toString()
            }
        }
    }

    override suspend fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        MethodTable.deleteWhere { (MethodTable.groupId eq groupId) and (MethodTable.createdAt less createdBefore.atStartOfDay()) }
    }

    override suspend fun deleteAllByBuildId(groupId: String, appId: String, buildId: String) {
        MethodTable.deleteWhere {
            (MethodTable.groupId eq groupId) and
                    (MethodTable.appId eq appId) and
                    (MethodTable.buildId eq buildId)
        }
    }
}