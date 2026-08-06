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
        skipIfLocked: Boolean = false,
    ): List<EtlProcessingResult>

    /**
     * Reruns all pipelines in the orchestrator for the given context, with an option to delete existing data.
     *
     * When [period] is bounded ([EtlPeriod.isBounded]) the run is scoped to that day range: the
     * extraction window is derived from the period, data deletion (when [withDataDeletion]) is
     * limited to rows whose `created_at_day` falls in the period, and the run keeps its own
     * watermark in its own metadata/run-lock rows — independent of the incremental [run] and of
     * other, non-overlapping periods (which may run in parallel).
     *
     * @param context The ETL context containing identifiers for the data to process.
     * @param initTimestamp Lower time bound used when [period] is unbounded.
     * @param finalTimestamp Upper time bound used when [period] is unbounded. If null, calculated on the implementation side.
     * @param period The day range to reprocess, or [EtlPeriod.UNBOUNDED] for a full reprocess.
     * @param withDataDeletion If true, existing data (scoped to [period]) is deleted before reprocessing.
     * @return A list of EtlProcessingResult
     */
    suspend fun rerun(
        context: EtlContext,
        initTimestamp: Instant = Instant.EPOCH,
        finalTimestamp: Instant? = null,
        period: EtlPeriod = EtlPeriod.UNBOUNDED,
        withDataDeletion: Boolean = false,
        skipIfLocked: Boolean = false,
    ): List<EtlProcessingResult>

    /**
     * Resumes all interrupted or failed bounded-period reruns (see
     * [EtlMetadataRepository.getUnfinishedTargets]) so that back-fills continue after a restart.
     * Each unfinished `(context, period)` continues from its persisted watermark (no data or
     * metadata deletion). Overlapping unfinished periods are serialized by the run-lock.
     *
     * @param context When non-null, only unfinished periods for this context are resumed.
     * @return A list of EtlProcessingResult for the resumed periods.
     */
    suspend fun resumeUnfinished(
        context: EtlContext? = null,
    ): List<EtlProcessingResult>
}

