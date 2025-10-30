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

import com.epam.drill.admin.metrics.etl.EtlMetadata
import com.epam.drill.admin.metrics.etl.EtlMetadataRepository
import com.epam.drill.admin.metrics.etl.EtlOrchestrator
import com.epam.drill.admin.metrics.etl.EtlPipeline
import com.epam.drill.admin.metrics.etl.EtlProcessingResult
import com.epam.drill.admin.metrics.etl.EtlStatus
import mu.KotlinLogging
import java.time.Instant
import kotlin.system.measureTimeMillis

open class EtlOrchestratorImpl(
    open val pipelines: List<EtlPipeline<*>>,
    open val metadataRepository: EtlMetadataRepository,
    open val batchSize: Int = 100
) : EtlOrchestrator {
    private val logger = KotlinLogging.logger {}

    override suspend fun runAll(): List<EtlProcessingResult> {
        logger.debug("ETL started...")
        val results = mutableListOf<EtlProcessingResult>()
        val duration = measureTimeMillis {
            for (pipeline in pipelines) {
                val result = run(pipeline.name)
                results.add(result)
            }
        }
        logger.info {
            val rowsProcessed = results.sumOf { it.rowsProcessed }
            val failures = results.count { !it.success }
            if (rowsProcessed == 0 && failures == 0)
                "ETL completed in ${duration}ms, no new rows"
            else
                "ETL completed in ${duration}ms, rows processed: $rowsProcessed, failures: $failures"
        }
        return results
    }

    override suspend fun run(pipelineName: String): EtlProcessingResult {
        val pipeline = pipelines.find { it.name == pipelineName }
            ?: throw IllegalArgumentException("Unknown pipeline: $pipelineName")
        val metadata = metadataRepository.getMetadata(pipelineName)
            ?: EtlMetadata(
                pipelineName = pipelineName,
                lastProcessedAt = Instant.EPOCH,
                lastRunAt = Instant.EPOCH,
                duration = 0,
                status = EtlStatus.NEVER_RUN,
                rowsProcessed = 0,
                errorMessage = null
            )
        val snapshotTime = Instant.now()
        try {
            val result = pipeline.execute(
                sinceTimestamp = metadata.lastProcessedAt,
                untilTimestamp = snapshotTime,
                batchSize = batchSize
            )
            metadataRepository.saveMetadata(
                metadata.copy(
                    lastProcessedAt = result.lastProcessedAt,
                    lastRunAt = snapshotTime,
                    duration = result.duration,
                    status = if (result.success) EtlStatus.SUCCESS else EtlStatus.FAILURE,
                    rowsProcessed = result.rowsProcessed,
                    errorMessage = result.errorMessage
                )
            )
            return result
        } catch (e: Exception) {
            logger.error("ETL pipeline [$pipelineName] failed: ${e.message}", e)
            val duration = Instant.now().toEpochMilli() - snapshotTime.toEpochMilli()
            metadataRepository.saveMetadata(
                metadata.copy(
                    lastRunAt = snapshotTime,
                    duration = duration,
                    status = EtlStatus.FAILURE,
                    errorMessage = e.message
                )
            )
            return EtlProcessingResult(
                pipelineName = pipelineName,
                lastProcessedAt = metadata.lastProcessedAt,
                rowsProcessed = 0,
                duration = duration,
                success = false,
                errorMessage = e.message
            )
        }
    }
}

