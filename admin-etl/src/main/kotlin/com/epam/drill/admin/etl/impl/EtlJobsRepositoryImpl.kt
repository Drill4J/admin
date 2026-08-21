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

import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlDailyStatus
import com.epam.drill.admin.etl.EtlDailyStatusRow
import com.epam.drill.admin.etl.EtlJob
import com.epam.drill.admin.etl.EtlJobResult
import com.epam.drill.admin.etl.EtlJobStatus
import com.epam.drill.admin.etl.EtlJobsRepository
import com.epam.drill.admin.etl.EtlPeriod
import com.epam.drill.admin.etl.table.DateRangeValue
import com.epam.drill.admin.etl.table.DaterangeOverlaps
import com.epam.drill.admin.etl.table.EtlJobsTable
import com.epam.drill.admin.etl.table.TimestampPlusSeconds
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.updateReturning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class EtlJobsRepositoryImpl(
    private val database: Database,
    dbSchema: String = "metrics",
    jobsTableName: String = "etl_jobs",
) : EtlJobsRepository {
    private val qualifiedName: String = "$dbSchema.$jobsTableName"
    private val jobsTable: EtlJobsTable = EtlJobsTable(qualifiedName)

    override suspend fun scheduleJob(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): EtlJob? = newSuspendedTransaction(db = database) {
        runCatching {
            jobsTable.insert {
                it[jobsTable.etlName] = etlName
                it[groupId] = context.groupId
                it[appId] = context.appId ?: ""
                it[buildId] = context.buildId ?: ""
                it[testSessionId] = context.testSessionId ?: ""
                it[testDefinitionId] = context.testDefinitionId ?: ""
                it[jobsTable.period] = period.toDateRangeValue()
                it[status] = EtlJobStatus.IDLE.name
                it[createdAt] = CurrentDateTime
                it[updatedAt] = CurrentDateTime
            }
            EtlJob(
                etlName = etlName,
                context = context,
                period = period,
            )
        }.getOrNull()
    }

    override suspend fun findResumable(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): List<EtlJob> = newSuspendedTransaction(db = database) {
        jobsTable.selectAll()
            .andWhere { sameEtl(etlName) and sameContext(context) and overlaps(period) and resumableStatus() }
            .map(::mapJob)
    }

    override suspend fun cancelJobs(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): List<EtlJobResult> = newSuspendedTransaction(db = database) {
        jobsTable.updateReturning(
            where = { sameEtl(etlName) and sameContext(context) and overlaps(period) and activeStatus() },
        ) {
            it[status] = EtlJobStatus.CANCELLING.name
            it[updatedAt] = CurrentDateTime
        }.map(::mapJobResult)
    }

    override suspend fun lockJob(
        job: EtlJob,
        workerId: String,
        leaseSeconds: Long,
    ): Boolean = newSuspendedTransaction(db = database) {
        jobsTable.update(where = { sameJob(job) and resumableStatus() }) {
            it[status] = EtlJobStatus.RUNNING.name
            it[jobsTable.workerId] = workerId
            it[lockExpiresAt] = TimestampPlusSeconds(leaseSeconds)
            it[startedAt] = CurrentTimestamp
            it[finishedAt] = null
            it[updatedAt] = CurrentDateTime
        } > 0
    }

    override suspend fun extendLease(job: EtlJob, workerId: String, leaseSeconds: Long): Boolean {
        return newSuspendedTransaction(db = database) {
            jobsTable.update(where = { sameJob(job) and sameWorker(workerId) and jobIsRunning() }) {
                it[lockExpiresAt] = TimestampPlusSeconds(leaseSeconds)
                it[updatedAt] = CurrentDateTime
            } > 0
        }
    }

    override suspend fun markIdle(job: EtlJob, workerId: String, processedUntilTimestamp: Instant) {
        newSuspendedTransaction(db = database) {
            jobsTable.update(where = { sameJob(job) and sameWorker(workerId) and jobIsRunning() }) {
                it[status] = EtlJobStatus.IDLE.name
                it[jobsTable.processedUntilTimestamp] = processedUntilTimestamp
                it[finishedAt] = CurrentTimestamp
                it[jobsTable.workerId] = null
                it[lockExpiresAt] = null
                it[updatedAt] = CurrentDateTime
            }
        }
    }

    override suspend fun markCompleted(job: EtlJob, workerId: String, processedUntilTimestamp: Instant) {
        newSuspendedTransaction(db = database) {
            jobsTable.update(where = { sameJob(job) and sameWorker(workerId) and jobIsRunning() }) {
                it[status] = EtlJobStatus.COMPLETED.name
                it[jobsTable.processedUntilTimestamp] = processedUntilTimestamp
                it[finishedAt] = CurrentTimestamp
                it[lockExpiresAt] = null
                it[updatedAt] = CurrentDateTime
            }
        }
    }

    override suspend fun markCancelled(job: EtlJob, workerId: String) {
        newSuspendedTransaction(db = database) {
            jobsTable.update(where = { sameJob(job) and sameWorker(workerId) and cancellingOrRunning() }) {
                it[status] = EtlJobStatus.CANCELLED.name
                it[finishedAt] = CurrentTimestamp
                it[lockExpiresAt] = null
                it[updatedAt] = CurrentDateTime
            }
        }
    }

    override suspend fun markError(job: EtlJob, workerId: String, errorMessage: String?) {
        newSuspendedTransaction(db = database) {
            jobsTable.update(where = { sameJob(job) and sameWorker(workerId) and jobIsRunning() }) {
                it[status] = EtlJobStatus.ERROR.name
                it[jobsTable.errorMessage] = errorMessage
                it[finishedAt] = CurrentTimestamp
                it[lockExpiresAt] = null
                it[updatedAt] = CurrentDateTime
            }
        }
    }

    override suspend fun reapExpiredCancelling(job: EtlJob): EtlJobResult? = newSuspendedTransaction(db = database) {
        jobsTable.updateReturning(
            where = {
                sameJob(job) and jobIsCancelling() and
                        (jobsTable.lockExpiresAt.isNull() or jobsTable.lockExpiresAt.less(CurrentTimestamp))
            },
        ) {
            it[status] = EtlJobStatus.CANCELLED.name
            it[finishedAt] = CurrentTimestamp
            it[lockExpiresAt] = null
            it[updatedAt] = CurrentDateTime
        }.map(::mapJobResult).firstOrNull()
    }

    override suspend fun getActiveJobs(
        etlName: String,
        context: EtlContext?,
        period: EtlPeriod,
    ): List<EtlJobResult> = newSuspendedTransaction(db = database) {
        jobsTable.selectAll()
            .andWhere {
                sameEtl(etlName) and
                    (context?.let { sameContext(it) } ?: Op.TRUE) and
                        activeStatus() }
            .map(::mapJobResult)
    }

    override suspend fun countRunningJobs(etlName: String?, context: EtlContext?): Long =
        newSuspendedTransaction(db = database) {
            jobsTable.selectAll()
                .andWhere {
                    (etlName?.let { sameEtl(it) } ?: Op.TRUE) and
                            (context?.let { sameContext(it) } ?: Op.TRUE) and
                            (jobsTable.status eq EtlJobStatus.RUNNING.name)
                }
                .count()
        }

    override suspend fun getActiveJob(job: EtlJob): EtlJobResult? = newSuspendedTransaction(db = database) {
        jobsTable.selectAll()
            .andWhere { sameJob(job) and activeStatus() }
            .map(::mapJobResult)
            .singleOrNull()
    }

    override suspend fun getDailyStatuses(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): List<EtlDailyStatusRow> {
        require(period.from != null && period.to != null) { "getDailyStatuses requires a bounded period" }
        val jobs = newSuspendedTransaction(db = database) {
            jobsTable.selectAll()
                .andWhere { sameEtl(etlName) and sameContext(context) and overlaps(period) }
                .orderBy(jobsTable.startedAt, SortOrder.DESC)
                .map(::mapJobResult)
        }
        val days = generateSequence(period.from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(period.to) }
            .toList()
        return days.map { day -> EtlDailyStatusRow(day, dayStatus(day, jobs)) }
    }

    override suspend fun getLastProcessedTimestamp(
        etlName: String,
        context: EtlContext,
    ): Instant? = newSuspendedTransaction(db = database) {
        jobsTable.selectAll()
            .andWhere { sameEtl(etlName) and sameContext(context) and activeStatus() }
            .mapNotNull { it[jobsTable.processedUntilTimestamp] }
            .minOrNull()
    }

    override suspend fun updateProcessedUntilTimestamp(
        job: EtlJob,
        workerId: String,
        processedUntilTimestamp: Instant
    ) {
        newSuspendedTransaction(db = database) {
            jobsTable.update(where = { sameJob(job) and sameWorker(workerId) }) {
                it[jobsTable.processedUntilTimestamp] = processedUntilTimestamp
                it[updatedAt] = CurrentDateTime
            }
        }
    }

    private fun dayStatus(day: LocalDate, jobs: List<EtlJobResult>): EtlDailyStatus {
        val covering = jobs.filter { jobProgress ->
            val from = jobProgress.job.period.from ?: LocalDate.MIN
            val to = jobProgress.job.period.to ?: LocalDate.MAX
            !day.isBefore(from) && !day.isAfter(to)
        }
        if (covering.isEmpty()) return EtlDailyStatus.UNLOADED
        val coveredJob = covering.first()
        val startOfNextDay = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val startOfCurrentDay = day.atStartOfDay().toInstant(ZoneOffset.UTC)
        //job is extracting data for the first day of the period
        if (coveredJob.processedUntilTimestamp == null && day == coveredJob.job.period.from) {
            return EtlDailyStatus.RUNNING
        }
        //job has already processed data for the day
        if (coveredJob.processedUntilTimestamp?.isAfter(startOfNextDay) ?: false) {
            return EtlDailyStatus.COMPLETED
        }
        //job has not yet processed data for the day
        if (coveredJob.processedUntilTimestamp?.isBefore(startOfCurrentDay) ?: true) {
            return when (coveredJob.status) {
                EtlJobStatus.RUNNING -> EtlDailyStatus.SCHEDULED
                EtlJobStatus.IDLE -> EtlDailyStatus.SCHEDULED
                EtlJobStatus.CANCELLING -> EtlDailyStatus.SCHEDULED
                EtlJobStatus.ERROR, EtlJobStatus.CANCELLED -> EtlDailyStatus.FAILED
                EtlJobStatus.COMPLETED -> EtlDailyStatus.UNLOADED
            }
        }
        //job is processing data for the day
        return when (coveredJob.status) {
            EtlJobStatus.RUNNING -> EtlDailyStatus.RUNNING
            EtlJobStatus.IDLE -> EtlDailyStatus.RUNNING
            EtlJobStatus.CANCELLING -> EtlDailyStatus.RUNNING
            EtlJobStatus.ERROR, EtlJobStatus.CANCELLED -> EtlDailyStatus.FAILED
            EtlJobStatus.COMPLETED -> EtlDailyStatus.COMPLETED
        }
    }

    private fun mapJob(row: ResultRow): EtlJob {
        val range = row[jobsTable.period]
        return EtlJob(
            etlName = row[jobsTable.etlName],
            context = EtlContext(
                groupId = row[jobsTable.groupId],
                appId = row[jobsTable.appId].ifEmpty { null },
                buildId = row[jobsTable.buildId].ifEmpty { null },
                testSessionId = row[jobsTable.testSessionId].ifEmpty { null },
                testDefinitionId = row[jobsTable.testDefinitionId].ifEmpty { null },
            ),
            period = EtlPeriod.fromStored(range.from, range.to),
        )
    }

    private fun mapJobResult(row: ResultRow): EtlJobResult {
        return EtlJobResult(
            job = mapJob(row),
            status = EtlJobStatus.valueOf(row[jobsTable.status]),
            workerId = row[jobsTable.workerId],
            lockExpiresAt = row[jobsTable.lockExpiresAt],
            errorMessage = row[jobsTable.errorMessage],
            processedUntilTimestamp = row[jobsTable.processedUntilTimestamp],
        )
    }

    private fun sameEtl(etlName: String): Op<Boolean> = jobsTable.etlName eq etlName

    private fun sameContext(context: EtlContext): Op<Boolean> =
        (jobsTable.groupId eq context.groupId) and
                (jobsTable.appId eq context.appId.orEmpty()) and
                (jobsTable.buildId eq context.buildId.orEmpty()) and
                (jobsTable.testSessionId eq context.testSessionId.orEmpty()) and
                (jobsTable.testDefinitionId eq context.testDefinitionId.orEmpty())

    private fun sameJob(job: EtlJob): Op<Boolean> =
        sameEtl(job.etlName) and sameContext(job.context) and (jobsTable.period eq job.period.toDateRangeValue())

    private fun sameWorker(workerId: String): Op<Boolean> = jobsTable.workerId eq workerId

    private fun jobIsRunning(): Op<Boolean> = jobsTable.status eq EtlJobStatus.RUNNING.name

    private fun jobIsCancelling(): Op<Boolean> = jobsTable.status eq EtlJobStatus.CANCELLING.name

    private fun cancellingOrRunning(): Op<Boolean> =
        (jobsTable.status eq EtlJobStatus.CANCELLING.name) or (jobsTable.status eq EtlJobStatus.RUNNING.name)

    private fun overlaps(period: EtlPeriod): Op<Boolean> =
        DaterangeOverlaps(jobsTable.period, period.toDateRangeValue())

    /**
     * Matches rows that the GIST exclusion constraint currently blocks — i.e. any row that
     * still holds its overlap slot: IDLE, RUNNING, or CANCELLING (worker winding down).
     */
    private fun activeStatus(): Op<Boolean> =
        (jobsTable.status eq EtlJobStatus.IDLE.name) or
                (jobsTable.status eq EtlJobStatus.RUNNING.name) or
                (jobsTable.status eq EtlJobStatus.CANCELLING.name)

    private fun resumableStatus(): Op<Boolean> =
        (jobsTable.status eq EtlJobStatus.IDLE.name) or
                ((jobsTable.status eq EtlJobStatus.RUNNING.name) and (jobsTable.lockExpiresAt.less(CurrentTimestamp)))
}

private fun EtlPeriod.toDateRangeValue(): DateRangeValue =
    DateRangeValue(from = storedFrom, to = storedTo)
