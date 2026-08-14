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

import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlDailyStatus
import com.epam.drill.admin.etl.EtlDailyStatusRow
import com.epam.drill.admin.etl.EtlJobResult
import com.epam.drill.admin.etl.EtlLauncher
import com.epam.drill.admin.etl.EtlPeriod
import com.epam.drill.admin.etl.model.EtlJobView
import com.epam.drill.admin.etl.service.EtlService
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import com.epam.drill.admin.writer.rawdata.views.GroupSettingsView
import mu.KotlinLogging
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset.UTC

class EtlServiceImpl(
    private val defaultLauncher: EtlLauncher,
    private val testDefinitionCoverageLauncher: EtlLauncher,
    private val settingsService: SettingsService,
    private val maxWorkers: Int,
) : EtlService {
    private val logger = KotlinLogging.logger {}

    /** Fallback history window (in days) used when a group has no `metricsPeriodDays` configured. */
    private val defaultHistoryDays = 365L

    override suspend fun refresh(groupId: String?) {
        forEachContext(groupId) { context ->
            defaultLauncher.resume(context, EtlPeriod.TODAY, skipIfRunning = true).takeIf { it.isNotEmpty() }
                ?: defaultLauncher.schedule(context, EtlPeriod.FROM_TODAY, 1).map {
                    defaultLauncher.run(it, skipIfRunning = true)
                }
        }
    }

    override suspend fun forceRefresh(groupId: String?, snapshotTimestamp: Instant?): Instant {
        return forEachContext(groupId) { context ->
            defaultLauncher.resume(context, EtlPeriod.TODAY, snapshotTimestamp, skipIfRunning = false)
                .takeIf { it.isNotEmpty() }
                ?: defaultLauncher.schedule(context, EtlPeriod.FROM_TODAY, 1).map {
                    defaultLauncher.run(it, snapshotTimestamp, skipIfRunning = false)
                }
        }.minOf { it.processedUntilTimestamp ?: Instant.EPOCH }
    }

    override suspend fun scheduleUnloadedDays(groupId: String?) {
        val today = LocalDate.now(UTC)
        forEachContextWithPeriodFrom(groupId) { context, from ->
            val historyPeriod = EtlPeriod(from, today)

            val unloadedDays =
                defaultLauncher.getDailyStatuses(context, historyPeriod).filter { it.status == EtlDailyStatus.UNLOADED }
                    .map { it.day }
            val unplannedPeriods = combineIntoPeriods(unloadedDays)

            if (unplannedPeriods.isNotEmpty()) {
                val workersPerPeriod = (maxWorkers / unplannedPeriods.size).takeIf { it > 0 } ?: 1
                for (period in unplannedPeriods) {
                    defaultLauncher.schedule(context, period, workersPerPeriod)
                }
                for (period in unplannedPeriods) {
                    defaultLauncher.resume(context, period)
                }
            }
        }
    }

    override suspend fun rerunDateRange(groupId: String?, from: LocalDate?, to: LocalDate?) {
        val today = LocalDate.now(UTC)
        forEachContextWithPeriodFrom(groupId) { context, resolvedFrom ->
            val resolvedTo = to ?: today
            if (resolvedTo != today) {
                val period = EtlPeriod(resolvedFrom, resolvedTo)
                defaultLauncher.rerun(context, period, maxWorkers, withDataDeletion = true)
            } else {
                val period = EtlPeriod(resolvedFrom, today.minusDays(1))
                defaultLauncher.rerun(context, period, maxWorkers, withDataDeletion = true)
                defaultLauncher.rerun(context, EtlPeriod.FROM_TODAY, 1, withDataDeletion = true)
            }
        }
    }

    override suspend fun rerunAllData(groupId: String?) {
        rerunDateRange(groupId, null, null)
    }

    override suspend fun rerunToday(groupId: String?) {
        val today = LocalDate.now(UTC)
        rerunDateRange(groupId, today, null)
    }

    override suspend fun runIdleJobs(groupId: String?) {
        forEachContextWithPeriodFrom(groupId) { context, from ->
            defaultLauncher.resume(context, EtlPeriod(from, null))
        }
    }

    override suspend fun getDailyStatuses(groupId: String, from: LocalDate?, to: LocalDate?): List<EtlDailyStatusRow> {
        val context = EtlContext(groupId)
        val resolvedFrom = from ?: resolveHistoryStart(settingsService.getGroupSettings(groupId))
        val resolvedTo = to ?: LocalDate.now(UTC)
        val period = EtlPeriod(resolvedFrom, resolvedTo)
        return defaultLauncher.getDailyStatuses(context, period)
    }

    override suspend fun getLastProcessedTimestamp(groupId: String): Instant? {
        val context = EtlContext(groupId)
        return defaultLauncher.getLastProcessedTimestamp(context)
    }

    override suspend fun loadTestDefinitionCoverage(
        groupId: String, testSessionId: String, testDefinitionId: String, snapshotTimestamp: Instant?
    ) {
        val context = EtlContext(
            groupId = groupId, testSessionId = testSessionId, testDefinitionId = testDefinitionId
        )
        testDefinitionCoverageLauncher.resume(context, EtlPeriod.UNBOUNDED, snapshotTimestamp, skipIfRunning = false)
            .takeIf { it.isNotEmpty() }
            ?: testDefinitionCoverageLauncher.schedule(context, EtlPeriod.UNBOUNDED, 1).map {
                testDefinitionCoverageLauncher.run(it, skipIfRunning = false)
            }
    }

    override suspend fun getActiveJobs(
        groupId: String?,
        from: LocalDate?,
        to: LocalDate?
    ): List<EtlJobView> {
        val period = EtlPeriod(from, to)
        val context = groupId?.let { EtlContext(groupId = it) }
        return defaultLauncher.getActiveJobs(context, period).map { it ->
            EtlJobView(
                groupId = it.job.context.groupId,
                workerId = it.workerId,
                status = it.status,
                fromDay = it.job.period.from?.toString(),
                toDay = it.job.period.to?.toString(),
                processedUntilTimestamp = it.processedUntilTimestamp.toString(),
            )
        }
    }

    /** Combines a (sorted or unsorted) list of individual days into contiguous [EtlPeriod]s. */
    private fun combineIntoPeriods(days: List<LocalDate>): List<EtlPeriod> {
        if (days.isEmpty()) return emptyList()
        val sorted = days.distinct().sorted()
        val periods = mutableListOf<EtlPeriod>()
        var rangeStart = sorted.first()
        var rangeEnd = sorted.first()
        for (day in sorted.drop(1)) {
            if (day == rangeEnd.plusDays(1)) {
                rangeEnd = day
            } else {
                periods += EtlPeriod(rangeStart, rangeEnd)
                rangeStart = day
                rangeEnd = day
            }
        }
        periods += EtlPeriod(rangeStart, rangeEnd)
        return periods
    }

    private suspend fun forEachContext(
        groupId: String?, block: suspend (EtlContext) -> List<EtlJobResult>
    ): List<EtlJobResult> {
        return if (groupId != null) {
            block(EtlContext(groupId))
        } else {
            settingsService.getAllGroupSettings().keys.flatMap { block(EtlContext(it)) }
        }
    }

    private suspend fun forEachContextWithPeriodFrom(
        groupId: String?, from: LocalDate? = null, block: suspend (EtlContext, LocalDate) -> Unit
    ) {
        if (groupId != null && from != null) {
            block(EtlContext(groupId), from)
        } else if (groupId != null) {
            val groupSettings = settingsService.getGroupSettings(groupId)
            val historyStart = resolveHistoryStart(groupSettings)
            block(EtlContext(groupId), historyStart)
        } else {
            settingsService.getAllGroupSettings().forEach { (groupId, settings) ->
                if (from != null) {
                    block(EtlContext(groupId), from)
                } else {
                    val historyStart = resolveHistoryStart(settings)
                    block(EtlContext(groupId), historyStart)
                }
            }
        }
    }

    private fun resolveHistoryStart(groupSettings: GroupSettingsView): LocalDate {
        val today = LocalDate.now()
        return groupSettings.metricsPeriodDays?.let {
            today.minusDays(it.toLong())
        } ?: today.minusDays(defaultHistoryDays)
    }
}