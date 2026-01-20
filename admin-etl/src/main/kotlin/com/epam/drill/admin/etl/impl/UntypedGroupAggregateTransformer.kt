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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class UntypedGroupAggregateTransformer(
    override val name: String,
    private val bufferSize: Int,
    private val groupKeys: List<String>,
    private val aggregate: (current: UntypedRow, next: UntypedRow) -> UntypedRow
) : DataTransformer<UntypedRow, UntypedRow> {
    override suspend fun transform(
        groupId: String,
        collector: Flow<UntypedRow>
    ): Flow<UntypedRow> = flow {
        val buffer = mutableMapOf<List<Any?>, UntypedRow>()

        collector.collect { row ->
            val groupKey = groupKeys.map { row[it] }
            val current = buffer[groupKey]
            val aggregated = if (current == null) {
                row
            } else {
                aggregate(current, row)
            }
            buffer[groupKey] = aggregated

            // Emit buffer contents when full
            if (buffer.size >= bufferSize) {
                buffer.forEach { emit(it.value) }
                buffer.clear()
            }
        }

        // Emit remaining buffered items
        buffer.forEach { emit(it.value) }
    }
}