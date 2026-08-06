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
import com.epam.drill.admin.etl.EtlPeriod
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.service.EtlService
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import com.epam.drill.admin.writer.rawdata.views.GroupSettingsView
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import kotlin.collections.component1
import kotlin.collections.component2

class EtlServiceImpl(
    private val etlRepository: EtlMetadataRepository,
    private val etl: EtlOrchestrator,
    private val settingsService: SettingsService,
) : EtlService {
    override suspend fun refresh(
        groupId: String?,
        reset: Boolean,
        initTimestamp: Instant?,
        finalTimestamp: Instant?,
        fromDay: LocalDate?,
        toDay: LocalDate?,
        chunkDays: Int?,
        skipIfLocked: Boolean
    ): List<EtlProcessingResult> {
        val rerun = reset || initTimestamp != null || finalTimestamp != null
        val periods = buildPeriods(fromDay, toDay, chunkDays)
        val context = groupId?.let { EtlContext(it) }
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
            params.flatMap { (context, resolvedInitTimestamp) ->
                if (periods.isNotEmpty()) {
                    // Day-period rerun(s): each period runs independently (and, when
                    // non-overlapping, in parallel) with its own watermark and lock.
                    periods.map { period ->
                        async {
                            etl.rerun(
                                context,
                                period = period,
                                withDataDeletion = reset,
                                skipIfLocked = skipIfLocked,
                            )
                        }
                    }
                } else {
                    listOf(async {
                        if (rerun)
                            etl.rerun(
                                context,
                                resolvedInitTimestamp,
                                finalTimestamp,
                                withDataDeletion = reset,
                                skipIfLocked = skipIfLocked,
                            )
                        else
                            etl.run(context, resolvedInitTimestamp, finalTimestamp, skipIfLocked)
                    })
                }
            }.awaitAll().flatten()
        }

        return results
    }

    override suspend fun resumeUnfinished(): List<EtlProcessingResult> {
        return runBlocking {
            etl.resumeUnfinished()
        }
    }

    /**
     * Splits `[fromDay, toDay]` into [EtlPeriod]s of at most [chunkDays] days each. Returns a
     * single (possibly half-open) period when [chunkDays] is not a positive number or when a
     * bound is missing, and an empty list when no day bound is provided at all.
     */
    private fun buildPeriods(fromDay: LocalDate?, toDay: LocalDate?, chunkDays: Int?): List<EtlPeriod> {
        if (fromDay == null && toDay == null) return emptyList()
        if (chunkDays == null || chunkDays <= 0 || fromDay == null || toDay == null) {
            return listOf(EtlPeriod(fromDay, toDay))
        }
        val from: LocalDate = fromDay
        val to: LocalDate = toDay
        require(!from.isAfter(to)) { "fromDay ($from) must not be after toDay ($to)" }
        val periods = mutableListOf<EtlPeriod>()
        var start = from
        while (!start.isAfter(to)) {
            val end = minOf(start.plusDays((chunkDays - 1).toLong()), to)
            periods += EtlPeriod(start, end)
            start = end.plusDays(1)
        }
        return periods
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