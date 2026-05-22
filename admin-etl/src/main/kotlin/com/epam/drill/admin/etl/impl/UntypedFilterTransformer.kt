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
package com.epam.drill.admin.etl.impl

import com.epam.drill.admin.etl.DataTransformer
import com.epam.drill.admin.etl.UntypedRow
import com.epam.drill.admin.etl.config.EtlMeter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

class UntypedFilterTransformer(
    override val name: String,
    private val metrics: EtlMeter,
    private val predicate: (UntypedRow) -> Boolean
) : DataTransformer<UntypedRow, UntypedRow> {
    override suspend fun transform(
        groupId: String,
        collector: Flow<UntypedRow>,
    ): Flow<UntypedRow> {
        val rowsTransformed = metrics.rowsTransformed(name, groupId)
        val rowsEmitted = metrics.rowsEmitted(name, groupId)
        return flow {
            collector.filter {
                predicate(it).also {
                    rowsTransformed.increment()
                }
            }.collect { row ->
                rowsEmitted.increment()
                emit(row)
            }
        }
    }
}