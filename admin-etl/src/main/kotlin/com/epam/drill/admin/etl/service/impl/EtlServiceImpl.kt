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

import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.job.DEFAULT_ETL
import com.epam.drill.admin.etl.service.EtlService
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import com.epam.drill.admin.writer.rawdata.views.GroupSettingsView
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.quartz.JobDataMap
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import kotlin.collections.component1
import kotlin.collections.component2

class EtlServiceImpl(
    private val etlRepository: EtlMetadataRepository,
    private val etls: Map<String, EtlOrchestrator>,
    private val settingsService: SettingsService,
) : EtlService {
    @Suppress("UNCHECKED_CAST")
    override suspend fun refresh(
        context: EtlContext?,
        etlName: String?,
        reset: Boolean,
        initTimestamp: Instant?,
        finalTimestamp: Instant?
    ): List<EtlProcessingResult> {
        val rerun = reset || initTimestamp != null
        val orchestratorName = etlName ?: DEFAULT_ETL
        val etl = etls[orchestratorName]
            ?: throw IllegalArgumentException("Etl with name '$orchestratorName' not found")

        val params: Map<EtlContext, Instant> = runBlocking {
            if (context != null) {
                mapOf(context to (initTimestamp ?: resolveInitTimestamp(context.groupId)))
            } else {
                settingsService.getAllGroupSettings().map { (groupId, groupSettings) ->
                    EtlContext(groupId) to (initTimestamp ?: resolveInitTimestamp(groupSettings))
                }.toMap()
            }
        }

        val results: List<EtlProcessingResult> = runBlocking {
            params.map { (context, initTimestamp) ->
                async {
                    if (rerun)
                        etl.rerun(context, initTimestamp, finalTimestamp, withDataDeletion = reset)
                    else
                        etl.run(context, initTimestamp, finalTimestamp)
                }
            }.awaitAll().flatten()
        }

        return results
    }

    override suspend fun getRefreshStatus(groupId: String): Map<String, Any?> {
        val metadata = etlRepository.getAllMetadata(EtlContext(groupId = groupId))
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

    private suspend fun resolveInitTimestamp(groupId: String): Instant = resolveInitTimestamp(
        settingsService.getGroupSettings(groupId)
    )


    private fun resolveInitTimestamp(groupSettings: GroupSettingsView): Instant =
        groupSettings.metricsPeriodDays?.let {
            Instant.now().atZone(UTC).toLocalDate().minusDays(it.toLong()).atStartOfDay().toInstant(UTC)
        } ?: Instant.EPOCH

    private fun JobDataMap.getInstantValue(key: String): Instant? = get(key)?.let {
        when (it) {
            is Instant -> it
            is String -> Instant.parse(it)
            is Long -> Instant.ofEpochMilli(it)
            else -> throw IllegalArgumentException("Unsupported initTimestamp type: ${it::class}")
        }
    }
}