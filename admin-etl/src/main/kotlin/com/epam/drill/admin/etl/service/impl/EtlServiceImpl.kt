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
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.epam.drill.admin.writer.rawdata.repository.BuildRepository
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import com.epam.drill.admin.writer.rawdata.views.GroupSettingsView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val buildRepository: BuildRepository,
    private val buildLevelEtlNames: Set<String> = emptySet(),
    private val defaultEtlNames: Set<String> = setOf(DEFAULT_ETL),
    maxParallelism: Int,
    private val dispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(maxParallelism.coerceAtLeast(1)),
) : EtlService {

    override suspend fun refresh(
        context: EtlContext?,
        etlName: String?,
        reset: Boolean,
        initTimestamp: Instant?,
        finalTimestamp: Instant?,
        skipIfLocked: Boolean,
    ): List<EtlProcessingResult> {
        val rerun = reset || initTimestamp != null || finalTimestamp != null

        val orchestrators: List<EtlOrchestrator> = if (etlName != null) {
            listOf(
                etls[etlName]
                    ?: throw IllegalArgumentException("Etl with name [$etlName] not found")
            )
        } else {
            defaultEtlNames.mapNotNull { etls[it] }
        }

        return coroutineScope {
            orchestrators.map { etl ->
                val params = resolveParams(etl.name, context, initTimestamp)
                params.map { (etlContext, resolvedInitTimestamp) ->
                    async(dispatcher) {
                        if (rerun)
                            etl.rerun(
                                etlContext,
                                resolvedInitTimestamp,
                                finalTimestamp,
                                withDataDeletion = reset,
                                skipIfLocked = skipIfLocked
                            )
                        else
                            etl.run(etlContext, resolvedInitTimestamp, finalTimestamp, skipIfLocked)
                    }
                }.awaitAll().flatten()
            }.flatten()
        }
    }

    private suspend fun resolveParams(
        orchestratorName: String,
        context: EtlContext?,
        initTimestamp: Instant?,
    ): Map<EtlContext, Instant> {
        val contexts = resolveContexts(orchestratorName, context)
        val initTimestampByGroup = HashMap<String, Instant>()
        val params = LinkedHashMap<EtlContext, Instant>()
        for (etlContext in contexts) {
            val resolved = initTimestamp
                ?: initTimestampByGroup[etlContext.groupId]
                ?: resolveInitTimestamp(etlContext.groupId).also {
                    initTimestampByGroup[etlContext.groupId] = it
                }
            params[etlContext] = resolved
        }
        return params
    }

    private suspend fun resolveContexts(
        orchestratorName: String,
        context: EtlContext?,
    ): List<EtlContext> {
        return if (orchestratorName in buildLevelEtlNames) {
            resolveBuildContexts(context)
        } else if (context != null) {
            listOf(context)
        } else {
            settingsService.getAllGroupSettings().keys.map { EtlContext(it) }
        }
    }

    /**
     * Expands the given context into one context per finalized (VALID) build.
     * - groupId: from the context if present, otherwise every configured group.
     * - appId/buildId: taken from the context when set; otherwise resolved from the
     *   finalized-builds lookup (optionally narrowed by the context appId).
     */
    private suspend fun resolveBuildContexts(context: EtlContext?): List<EtlContext> {
        val groupIds = context?.groupId?.let { listOf(it) }
            ?: settingsService.getAllGroupSettings().keys.toList()
        return groupIds.flatMap { groupId ->
            val appId = context?.appId
            val buildId = context?.buildId
            if (buildId != null) {
                listOf(EtlContext(groupId = groupId, appId = appId, buildId = buildId))
            } else {
                transaction { buildRepository.getFinalizedBuilds(groupId, appId) }.map { build ->
                    EtlContext(groupId = groupId, appId = build.appId, buildId = build.id)
                }
            }
        }
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
}