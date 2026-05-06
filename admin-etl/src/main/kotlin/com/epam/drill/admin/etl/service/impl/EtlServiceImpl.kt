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
package com.epam.drill.admin.etl.service.impl

import com.epam.drill.admin.common.scheduler.DrillScheduler
import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.config.getUpdateMetricsEtlDataMap
import com.epam.drill.admin.etl.config.updateMetricsEtlJobKey
import com.epam.drill.admin.etl.service.EtlService
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EtlServiceImpl(
    private val scheduler: DrillScheduler,
    private val etlRepository: EtlMetadataRepository
) : EtlService {
    @Suppress("UNCHECKED_CAST")
    override suspend fun refresh(groupId: String?, reset: Boolean) {
        val params = getUpdateMetricsEtlDataMap(groupId, reset)
        val results = suspendCancellableCoroutine { continuation ->
            scheduler.triggerJob(updateMetricsEtlJobKey, params) { results, exception ->
                if (exception != null)
                    continuation.resumeWithException(exception)
                else
                    continuation.resume(results as List<EtlProcessingResult>)
            }
        }
        if (results.any { it.status != EtlStatus.SUCCESS }) {
            val errorMessages = results.filter { it.status == EtlStatus.FAILED }.joinToString(separator = "\n") {
                "Pipeline `${it.pipelineName}`: ${it.errorMessage ?: "Unknown error"}"
            }
            throw IllegalStateException("Error(s) occurred during ETL process:\n$errorMessages")
        }
    }

    override suspend fun getRefreshStatus(groupId: String): Map<String, Any?> {
        val metadata = etlRepository.getAllMetadata(groupId)
        if (metadata.isEmpty()) return emptyMap()

        val statusOrder = listOf(EtlStatus.FAILED, EtlStatus.EXTRACTING, EtlStatus.LOADING, EtlStatus.SUCCESS)
        val minStatus = metadata.minByOrNull { statusOrder.indexOf(it.status) }?.status ?: EtlStatus.SUCCESS
        fun Instant.toTimestamp() = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
        val maxLastProcessedAt = metadata.maxOfOrNull { it.lastProcessedAt.toTimestamp() }
        val maxLastRunAt = metadata.maxOfOrNull { it.lastRunAt.toTimestamp() }
        val errorMessages = metadata.mapNotNull { it.errorMessage }
        val sumDuration = metadata.sumOf { it.lastLoadDuration + it.lastExtractDuration }
        val sumRowsProcessed = metadata.sumOf { it.lastRowsProcessed }

        return buildMap {
            put("status", minStatus.name)
            put("lastProcessedAt", maxLastProcessedAt)
            put("lastRunAt", maxLastRunAt)
            if (errorMessages.isNotEmpty()) put("errorMessage", errorMessages.joinToString("; "))
            put("lastDuration", sumDuration)
            put("lastRowsProcessed", sumRowsProcessed)
        }
    }
}