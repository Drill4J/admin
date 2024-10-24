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
import org.jetbrains.exposed.sql.batchUpsert

class MethodRepositoryImpl: MethodRepository {
    override fun createMany(data: List<Method>) {
        MethodTable.batchUpsert(data, shouldReturnGeneratedValues = false) {
            this[MethodTable.id] = it.id
            this[MethodTable.buildId] = it.buildId
            this[MethodTable.classname] = it.classname
            this[MethodTable.name] = it.name
            this[MethodTable.params] = it.params
            this[MethodTable.returnType] = it.returnType
            this[MethodTable.probesStartPos] = it.probesStartPos
            this[MethodTable.bodyChecksum] = it.bodyChecksum
            this[MethodTable.signature] = it .signature
            this[MethodTable.probesCount] = it.probesCount
            this[MethodTable.annotations] = it.annotations.toString()
        }
    }

}