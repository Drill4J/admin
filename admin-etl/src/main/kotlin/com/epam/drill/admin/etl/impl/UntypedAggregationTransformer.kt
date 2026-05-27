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
import com.epam.drill.admin.etl.config.EtlMeter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class UntypedAggregationTransformer(
    override val name: String,
    private val bufferSize: Int,
    private val loggingFrequency: Int = 10,
    private val metrics: EtlMeter,
    private val groupKeys: List<String>,
    private val aggregate: (current: UntypedRow, next: UntypedRow) -> UntypedRow
) : DataTransformer<UntypedRow, UntypedRow> {
    private val logger = KotlinLogging.logger {}

    override suspend fun transform(
        groupId: String,
        collector: Flow<UntypedRow>
    ): Flow<UntypedRow> = flow {
        var isTransformationStarted = false
        val transformedRows = AtomicInteger()
        val aggregatedRows = metrics.rowsAggregated(name, groupId)
        val bufferOccupancy = metrics.aggregationBufferOccupancyRatio(name, groupId)
        val buffer = LruMap<List<Any?>, UntypedRow>(maxSize = bufferSize)

        trackProgressOf {
            try {
                collector.collect { row ->
                    if (!isTransformationStarted) {
                        logger.debug { "ETL transformer [$name] for group [$groupId] started transformation..." }
                        isTransformationStarted = true
                    }

                    val groupKey = groupKeys.map { row[it] }
                    val evicted = buffer.compute(groupKey) { value ->
                        if (value == null) {
                            row
                        } else {
                            aggregatedRows.increment()
                            aggregate(value, row)
                        }
                    }
                    transformedRows.incrementAndGet()
                    bufferOccupancy.set(if (bufferSize == 0) 0.0 else buffer.size.toDouble() / bufferSize.toDouble())
                    if (evicted != null) {
                        emit(evicted)
                    }
                }

                // Emit remaining aggregated rows
                buffer.evictAll { _, evicted ->
                    bufferOccupancy.set(if (bufferSize == 0) 0.0 else buffer.size.toDouble() / bufferSize.toDouble())
                    emit(evicted)
                }
            } catch (e: Exception) {
                logger.error(e) { "ETL transformer [$name] for group [$groupId] failed during transformation: ${e.message}" }
                throw e
            }
        }.every(loggingFrequency.seconds) {
            if (isTransformationStarted)
                logger.debug {
                    "ETL transformer [$name] for group [$groupId] transformed ${transformedRows.get()} rows" +
                            ", buffer occupancy: ${buffer.size}" +
                            ", buffer occupancy ratio: ${bufferOccupancy.get()}"
                }
        }
        if (isTransformationStarted) {
            logger.debug {
                "ETL transformer [$name] for group [$groupId] completed transformation for $transformedRows rows"
            }
        }
        bufferOccupancy.set(0.0)
    }
}