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
import com.epam.drill.admin.etl.flow.LruMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class UntypedAggregationTransformer(
    override val name: String,
    private val bufferSize: Int,
    private val groupKeys: List<String>,
    private val aggregate: (current: UntypedRow, next: UntypedRow) -> UntypedRow
) : DataTransformer<UntypedRow, UntypedRow> {

    override suspend fun transform(
        groupId: String,
        collector: Flow<UntypedRow>
    ): Flow<UntypedRow> = flow {
        var processedRows = 0
        var evictedRows = 0

        val emittingChannel = Channel<UntypedRow>(capacity = bufferSize)
        suspend fun drainChannel() {
            var next = emittingChannel.tryReceive().getOrNull()
            while (next != null) {
                emit(next)
                next = emittingChannel.tryReceive().getOrNull()
            }
        }
        val buffer = LruMap<List<Any?>, UntypedRow>(maxSize = bufferSize) { _, value ->
            evictedRows++
            emittingChannel.trySendBlocking(value)
        }

        collector.collect { row ->
            processedRows++
            val groupKey = groupKeys.map { row[it] }
            buffer.compute(groupKey) { value ->
                if (value == null) {
                    row
                } else {
                    aggregate(value, row)
                }
            }
            drainChannel()
        }

        // Emit remaining aggregated rows
        buffer.evictAll()
        drainChannel()
        emittingChannel.close()
    }
}