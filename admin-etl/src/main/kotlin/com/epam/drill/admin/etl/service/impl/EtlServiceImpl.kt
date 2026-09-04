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
import com.epam.drill.admin.etl.EtlDailyStatusRow
import com.epam.drill.admin.etl.EtlJob
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
    private val todayLauncher: EtlLauncher,
    private val historicalLauncher: EtlLauncher,
    private val testDefinitionCoverageLauncher: EtlLauncher,
    private val settingsService: SettingsService,
    private val maxWorkers: Int,
) : EtlService {
    private val logger = KotlinLogging.logger {}

    /** Fallback history window (in days) used when a group has no `metricsPeriodDays` configured. */
    private val defaultHistoryDays = 365L

    override suspend fun refresh(groupId: String?) {
        forEachContext(groupId) { context ->
            todayLauncher.resume(context, EtlPeriod.TODAY, skipIfRunning = true).takeIf { it.isNotEmpty() }
                ?: todayLauncher.schedule(context, EtlPeriod.FROM_TODAY, 1).map {
                    todayLauncher.run(it, skipIfRunning = true)
                }
        }
    }

    override suspend fun forceRefresh(groupId: String?, snapshotTimestamp: Instant?): Instant {
        return forEachContext(groupId) { context ->
            todayLauncher.resume(context, EtlPeriod.TODAY, snapshotTimestamp, skipIfRunning = false)
                .takeIf { it.isNotEmpty() }
                ?: todayLauncher.schedule(context, EtlPeriod.FROM_TODAY, 1).map {
                    todayLauncher.run(it, snapshotTimestamp, skipIfRunning = false)
                }.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("Cannot force refresh ETL, because some job is already running for group ${context.groupId}.")
        }.minOf { it.processedUntilTimestamp ?: Instant.EPOCH }
    }

    override suspend fun rerunDateRange(
        groupId: String?,
        from: LocalDate?,
        to: LocalDate?,
        workers: Int?
    ): List<EtlJobView> {
        val today = LocalDate.now(UTC)
        val yesterday = today.minusDays(1)
        check(to?.isBefore(today.plusDays(1)) ?: true) {
            "Cannot rerun ETL for future dates."
        }
        val resolvedTo = to?.takeIf { to.isBefore(today) } ?: yesterday
        val historyJobs = forEachContextWithPeriodFrom(groupId, from) { context, resolvedFrom ->
            val period = EtlPeriod(resolvedFrom, resolvedTo)
            historicalLauncher.rerun(context, period, workers ?: maxWorkers, withDataDeletion = true)
        }.map { it.toJobView() }
        val todayJobs = if (to == null || to.isEqual(today)) {
            rerunToday(groupId)
        } else {
            emptyList()
        }
        return (historyJobs + todayJobs)
    }

    override suspend fun rerunAllData(groupId: String?, workers: Int?): List<EtlJobView> {
        return rerunDateRange(groupId, null, null, workers)
    }

    override suspend fun rerunToday(groupId: String?): List<EtlJobView> {
        return forEachContext(groupId) { context ->
            todayLauncher.rerun(
                context = context,
                period = EtlPeriod.FROM_TODAY,
                workers = maxWorkers,
                withDataDeletion = true,
            )
        }.map { it.toJobView() }
    }

    override suspend fun runIdleJobs(groupId: String?): List<EtlJobView> {
        val today = LocalDate.now(UTC)
        val yesterday = today.minusDays(1)
        return forEachContextWithPeriodFrom(groupId) { context, from ->
            historicalLauncher.resume(context, EtlPeriod(from, yesterday))
        }.map { it.toJobView() }
    }

    override suspend fun getDailyStatuses(groupId: String, from: LocalDate?, to: LocalDate?): List<EtlDailyStatusRow> {
        val context = EtlContext(groupId)
        val resolvedFrom = from ?: resolveHistoryStart(settingsService.getGroupSettings(groupId))
        val resolvedTo = to ?: LocalDate.now(UTC)
        val period = EtlPeriod(resolvedFrom, resolvedTo)
        return historicalLauncher.getDailyStatuses(context, period)
    }

    override suspend fun getLastProcessedTimestamp(groupId: String): Instant? {
        val context = EtlContext(groupId)
        return todayLauncher.getLastProcessedTimestamp(context)
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
        val todayJobs = todayLauncher.getActiveJobs(context, period).map { it.toJobView() }
        val historicalJobs = historicalLauncher.getActiveJobs(context, period).map { it.toJobView() }
        return todayJobs + historicalJobs
    }

    override suspend fun cancelJobs(groupId: String?,
                                    from: LocalDate?,
                                    to: LocalDate?): List<EtlJobView> {
        check(to?.isBefore(LocalDate.now().plusDays(1)) ?: true) {
            "Cannot cancel ETL for future dates."
        }
        val today = LocalDate.now(UTC)
        return forEachContextWithPeriodFrom(groupId, from) { context, resolvedFrom ->
            val resolvedTo = to ?: today
            val period = EtlPeriod(resolvedFrom, resolvedTo)
            historicalLauncher.cancel(context, period)
        }.map { it.toJobView() }
    }

    private fun EtlJobResult.toJobView(): EtlJobView = EtlJobView(
        groupId = this.job.context.groupId,
        workerId = this.workerId,
        status = this.status,
        fromDay = this.job.period.from?.toString(),
        toDay = this.job.period.to?.toString(),
        processedUntilTimestamp = this.processedUntilTimestamp.toString(),
    )

    private suspend fun forEachContext(
        groupId: String?, block: suspend (EtlContext) -> List<EtlJobResult>
    ): List<EtlJobResult> {
        return if (groupId != null) {
            block(EtlContext(groupId))
        } else {
            settingsService.getAllGroupSettings().keys.flatMap { block(EtlContext(it)) }
        }
    }

    private suspend fun <T> forEachContextWithPeriodFrom(
        groupId: String?, from: LocalDate? = null, block: suspend (EtlContext, LocalDate) -> List<T>
    ): List<T> {
        if (groupId != null && from != null) {
            return block(EtlContext(groupId), from)
        } else if (groupId != null) {
            val groupSettings = settingsService.getGroupSettings(groupId)
            val historyStart = resolveHistoryStart(groupSettings)
            return block(EtlContext(groupId), historyStart)
        } else {
            val list = mutableListOf<T>()
            settingsService.getAllGroupSettings().forEach { (groupId, settings) ->
                if (from != null) {
                    list.addAll(block(EtlContext(groupId), from))
                } else {
                    val historyStart = resolveHistoryStart(settings)
                    list.addAll(block(EtlContext(groupId), historyStart))
                }
            }
            return list
        }
    }

    private fun resolveHistoryStart(groupSettings: GroupSettingsView): LocalDate {
        val today = LocalDate.now()
        return groupSettings.metricsPeriodDays?.let {
            today.minusDays(it.toLong())
        } ?: today.minusDays(defaultHistoryDays)
    }
}