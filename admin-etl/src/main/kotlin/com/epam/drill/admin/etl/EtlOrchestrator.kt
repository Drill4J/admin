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
 * EtlOrchestrator is responsible for:
 * - Coordinating the lifecycle of multiple pipelines.
 * - Storing and retrieving ETL metadata.
 * - Providing high-level monitoring and error handling.
 */
interface EtlOrchestrator {
    val name: String
    val pipelines: List<EtlPipeline<*, *>>

    /**
     * Runs all pipelines in the orchestrator for the given context and time range.
     * @param context The ETL context containing identifiers for the data to process.
     * @param initTimestamp The start of the time range for processing (inclusive).
     * @param finalTimestamp The end of the time range for processing (exclusive). If null, calculated on the implementation side.
     * @return A list of EtlProcessingResult
     */
    suspend fun run(
        context: EtlContext,
        initTimestamp: Instant = Instant.EPOCH,
        finalTimestamp: Instant? = null,
    ): List<EtlProcessingResult>

    /**
     * Reruns all pipelines in the orchestrator for the given context and time range, with an option to delete existing data.
     * @param context The ETL context containing identifiers for the data to process.
     * @param initTimestamp The start of the time range for processing (inclusive).
     * @param finalTimestamp The end of the time range for processing (exclusive). If null, calculated on the implementation side.
     * @param withDataDeletion If true, existing data in the target storage for the specified time range will be deleted before reprocessing.
     * @return A list of EtlProcessingResult
     */
    suspend fun rerun(
        context: EtlContext,
        initTimestamp: Instant = Instant.EPOCH,
        finalTimestamp: Instant? = null,
        withDataDeletion: Boolean
    ): List<EtlProcessingResult>
}

