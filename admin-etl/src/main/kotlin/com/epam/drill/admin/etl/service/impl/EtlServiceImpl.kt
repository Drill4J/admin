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
import java.time.ZoneId

class EtlServiceImpl(
    private val launcher: EtlLauncher,
    private val incrementalEtlName: String,
    private val historicalEtlName: String,
    private val testSessionCoverageEtlName: String,
    private val mergedCoverageEtlName: String,
    private val settingsService: SettingsService,
    private val maxWorkers: Int,
) : EtlService {
    private val logger = KotlinLogging.logger {}

    /** Fallback history window (in days) used when a group has no `metricsPeriodDays` configured. */
    private val defaultHistoryDays = 365L

    override suspend fun refresh(groupId: String?) {
        forEachContext(groupId) { context ->
            launcher.resume(incrementalEtlName, context, EtlPeriod.TODAY, skipIfRunning = true)
                .takeIf { it.isNotEmpty() }
                ?: launcher.schedule(incrementalEtlName, context, EtlPeriod.FROM_TODAY, 1).map {
                    launcher.run(it, skipIfRunning = true)
                }
        }
    }

    override suspend fun forceRefresh(groupId: String?, snapshotTimestamp: Instant?): Instant {
        return forEachContext(groupId) { context ->
            launcher.resume(incrementalEtlName, context, EtlPeriod.TODAY, snapshotTimestamp, skipIfRunning = false)
                .takeIf { it.isNotEmpty() }
                ?: launcher.schedule(incrementalEtlName, context, EtlPeriod.FROM_TODAY, 1).map {
                    launcher.run(it, snapshotTimestamp, skipIfRunning = false)
                }.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("Cannot force refresh ETL, because some job is already running for group ${context.groupId}.")
        }.minOf { it.processedUntilTimestamp ?: Instant.EPOCH }
    }

    override suspend fun rerunDateRange(
        groupId: String?,
        from: LocalDate?,
        to: LocalDate?,
        workers: Int?,
        withDataDeletion: Boolean
    ): List<EtlJobView> {
        val today = LocalDate.now(ZoneId.systemDefault())
        val yesterday = today.minusDays(1)
        check(to?.isBefore(today.plusDays(1)) ?: true) {
            "Cannot rerun ETL for future dates."
        }
        val resolvedTo = to?.takeIf { to.isBefore(today) } ?: yesterday
        val historyJobs = if (from == null || from.isBefore(today)) {
            forEachContextWithPeriod(groupId, from, resolvedTo) { context, period ->
                launcher.rerun(historicalEtlName, context, period, workers ?: maxWorkers, withDataDeletion)
            }.map { it.toJobView() }
        } else
            emptyList()
        val todayJobs = if (to == null || to.isEqual(today)) {
            rerunToday(groupId, withDataDeletion)
        } else {
            emptyList()
        }
        return (historyJobs + todayJobs)
    }

    override suspend fun rerunToday(groupId: String?, withDataDeletion: Boolean): List<EtlJobView> {
        return forEachContext(groupId) { context ->
            launcher.rerun(
                etlName = incrementalEtlName,
                context = context,
                period = EtlPeriod.FROM_TODAY,
                workers = maxWorkers,
                withDataDeletion = withDataDeletion,
            )
        }.map { it.toJobView() }
    }

    override suspend fun runIdleJobs(groupId: String?): List<EtlJobView> {
        val today = LocalDate.now(ZoneId.systemDefault())
        val yesterday = today.minusDays(1)
        return forEachContextWithPeriod(groupId, from = null, to = yesterday) { context, period ->
            launcher.resume(historicalEtlName, context, period)
        }.map { it.toJobView() }
    }

    override suspend fun getDailyStatuses(groupId: String, from: LocalDate?, to: LocalDate?): List<EtlDailyStatusRow> {
        val context = EtlContext(groupId)
        val today = LocalDate.now(ZoneId.systemDefault())
        val resolvedFrom = from ?: resolveHistoryStart(settingsService.getGroupSettings(groupId))
        val resolvedTo = to ?: today
        val period = EtlPeriod(resolvedFrom, resolvedTo)
        return launcher.getDailyStatuses(context, period)
    }

    override suspend fun getLastProcessedTimestamp(groupId: String): Instant? {
        val context = EtlContext(groupId)
        return launcher.getLastProcessedTimestamp(context)
    }

    override suspend fun loadTestSessionCoverage(
        groupId: String, testSessionId: String, snapshotTimestamp: Instant?
    ): List<EtlJobView> {
        val context = EtlContext(groupId = groupId, testSessionId = testSessionId)
        return (launcher.resume(
            testSessionCoverageEtlName,
            context,
            EtlPeriod.UNBOUNDED,
            snapshotTimestamp,
            skipIfRunning = true
        ).takeIf { it.isNotEmpty() }
            ?: launcher.schedule(testSessionCoverageEtlName, context, EtlPeriod.UNBOUNDED, 1).map {
                launcher.run(it, skipIfRunning = true)
            }).map { it.toJobView() }
    }

    override suspend fun reloadTestSessionCoverage(
        groupId: String, testSessionId: String,
        withDataDeletion: Boolean
    ): List<EtlJobView> {
        val context = EtlContext(groupId = groupId, testSessionId = testSessionId)
        return launcher.rerun(testSessionCoverageEtlName, context, EtlPeriod.UNBOUNDED, 1, withDataDeletion)
            .map { it.toJobView() }
    }

    override suspend fun getActiveJobs(
        groupId: String?,
        from: LocalDate?,
        to: LocalDate?
    ): List<EtlJobView> {
        val period = EtlPeriod(from, to)
        val context = groupId?.let { EtlContext(groupId = it) }
        return launcher.getActiveJobs(context, period).map { it.toJobView() }
    }

    override suspend fun cancelJobs(
        groupId: String?,
        from: LocalDate?,
        to: LocalDate?
    ): List<EtlJobView> {
        check(to?.isBefore(LocalDate.now().plusDays(1)) ?: true) {
            "Cannot cancel ETL for future dates."
        }
        val today = LocalDate.now(ZoneId.systemDefault())
        val resolvedTo = to ?: today
        return forEachContextWithPeriod(groupId, from, resolvedTo) { context, period ->
            launcher.cancel(historicalEtlName, context, period)
        }.map { it.toJobView() }
    }

    override suspend fun reloadMergedCoverage(
        groupId: String,
        appId: String,
        from: LocalDate?,
        to: LocalDate?
    ): List<EtlJobView> {
        val context = EtlContext(groupId = groupId, appId = appId)
        val period = EtlPeriod(from, to)
        return launcher.rerun(mergedCoverageEtlName, context, period, 1, withDataDeletion = true)
            .map { it.toJobView() }
    }

    private fun EtlJobResult.toJobView(): EtlJobView = EtlJobView(
        etlName = this.job.etlName,
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

    private suspend fun <T> forEachContextWithPeriod(
        groupId: String?,
        from: LocalDate? = null,
        to: LocalDate? = null,
        block: suspend (EtlContext, EtlPeriod) -> List<T>
    ): List<T> {
        if (groupId != null && from != null) {
            return block(EtlContext(groupId), EtlPeriod(from, to))
        } else if (groupId != null) {
            val groupSettings = settingsService.getGroupSettings(groupId)
            val historyStart = resolveHistoryStart(groupSettings)
            return block(EtlContext(groupId), EtlPeriod(historyStart, to))
        } else {
            val list = mutableListOf<T>()
            settingsService.getAllGroupSettings().forEach { (groupId, settings) ->
                if (from != null) {
                    list.addAll(block(EtlContext(groupId), EtlPeriod(from, to)))
                } else {
                    val historyStart = resolveHistoryStart(settings)
                    list.addAll(block(EtlContext(groupId), EtlPeriod(historyStart, to)))
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
