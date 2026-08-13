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
 * Orchestrates the ETL process by coordinating the pipelines.
 */
interface EtlOrchestrator {
    val name: String
    val pipelines: List<EtlPipeline<*, *>>

    /**
     * Runs all pipelines for its `(context, period)` up to [snapshotTimestamp].
     */
    suspend fun run(
        job: EtlJob,
        workerId: String,
        snapshotTimestamp: Instant? = null,
    ): EtlJobResult

    /**
     * Clears its pipelines' `etl_metadata` watermarks (and, when [withDataDeletion],
     * deletes previously loaded data via [EtlPipeline.cleanUp]) scoped to the job's period, then
     * runs the job from scratch (see [run]).
     */
    suspend fun rerun(
        job: EtlJob,
        workerId: String,
        withDataDeletion: Boolean = false,
    ): EtlJobResult
}

