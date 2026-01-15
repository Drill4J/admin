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
package com.epam.drill.admin.etl

import java.time.Instant

/**
 * EtlPipeline represents a single ETL flow connecting:
 * - one `DataExtractor` (source),
 * - one or more `DataLoader`-s (sinks).
 * It encodes business logic of how raw data becomes metric data.
 *
 * Core Concepts:
 * - **Composition**: EtlPipeline is typically constructed with specific extractor and loaders.
 * - **Concurrency control**: may use coroutines to run:
 *     - extractor in one coroutine,
 *     - loaders in another,
 *     - sharing a bounded buffer (`bufferSize`).
 * - **Backpressure**:
 *     - if loaders are slow, the buffer fills up and extractor suspends, keeping memory usage controlled.
 * - **Error propagation**:
 *     - failures in loaders or extractor are propagated to the pipeline,
 *     - pipeline may cancel child coroutines and report status to `EtlOrchestrator`.
 */
interface EtlPipeline<T: EtlRow> {
    val name: String
    val extractor: DataExtractor<T>
    val loaders: List<DataLoader<T>>
    suspend fun execute(
        groupId: String,
        sinceTimestampPerLoader: Map<String, Instant>,
        untilTimestamp: Instant,
        onExtractingProgress: suspend (EtlExtractingResult) -> Unit = {},
        onLoadingProgress: suspend (loaderName: String, result: EtlLoadingResult) -> Unit = { _, _ -> },
        onStatusChanged: suspend (loaderName: String, status: EtlStatus) -> Unit = { _, _ -> },
    ): EtlProcessingResult

    suspend fun cleanUp(groupId: String)
}