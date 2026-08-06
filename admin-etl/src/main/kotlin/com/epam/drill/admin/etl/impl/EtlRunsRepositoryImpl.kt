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
import com.epam.drill.admin.etl.EtlPeriod
import com.epam.drill.admin.etl.EtlRunStatus
import com.epam.drill.admin.etl.EtlRunsRepository
import com.epam.drill.admin.etl.table.EtlRunsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsertReturning
import java.time.Instant

class EtlRunsRepositoryImpl(
    private val database: Database,
    dbSchema: String = "metrics",
    runsTableName: String = "etl_runs",
) : EtlRunsRepository {
    private val qualifiedName: String = "$dbSchema.$runsTableName"
    private val runsTable: EtlRunsTable = EtlRunsTable(qualifiedName)

    override suspend fun tryAcquireLockAndStart(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        leaseSeconds: Long,
        period: EtlPeriod,
    ): Boolean = newSuspendedTransaction(db = database) {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(leaseSeconds)

        // Serialize concurrent acquisitions for the same orchestrator+context so the
        // overlap check below and the subsequent upsert are atomic w.r.t. each other.
        exec("SELECT pg_advisory_xact_lock(${advisoryLockKey(orchestratorName, context)})")

        if (period.isBounded && hasOverlappingActiveLock(orchestratorName, context, ownerId, period)) {
            return@newSuspendedTransaction false
        }

        val result = runsTable.upsertReturning(
            onUpdate = {
                it[runsTable.status] = EtlRunStatus.RUNNING.name
                it[runsTable.runsCount] = runsTable.runsCount + 1
                it[runsTable.lastStartedAt] = CurrentTimestamp
                it[runsTable.lastFinishedAt] = null

                it[runsTable.lockExpiresAt] = expiresAt
                it[runsTable.lockOwner] = ownerId

                it[runsTable.updatedAt] = CurrentDateTime
            },
            where = {
                sameOrchestrator(orchestratorName) and
                        sameContext(context) and
                        samePeriod(period) and
                        freeOrOwnedBy(ownerId)
            }
        ) {
            it[runsTable.orchestratorName] = orchestratorName
            it[groupId] = context.groupId
            it[appId] = context.appId ?: ""
            it[buildId] = context.buildId ?: ""
            it[instanceId] = context.instanceId ?: ""
            it[testSessionId] = context.testSessionId ?: ""
            it[testDefinitionId] = context.testDefinitionId ?: ""
            it[testLaunchId] = context.testLaunchId ?: ""

            it[periodFrom] = period.storedFrom
            it[periodTo] = period.storedTo

            it[runsTable.status] = EtlRunStatus.RUNNING.name
            it[runsTable.runsCount] = 1
            it[runsTable.lastStartedAt] = CurrentTimestamp
            it[runsTable.lastFinishedAt] = null

            it[runsTable.lockExpiresAt] = expiresAt
            it[runsTable.lockOwner] = ownerId

            it[updatedAt] = CurrentDateTime
        }

        result.map { it[runsTable.lockOwner] == ownerId }.count() > 0
    }

    override suspend fun extendLease(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        leaseSeconds: Long,
        period: EtlPeriod,
    ) {
        val expiresAt = Instant.now().plusSeconds(leaseSeconds)
        newSuspendedTransaction(db = database) {
            runsTable.update(where = {
                sameOrchestrator(orchestratorName) and
                        sameContext(context) and
                        samePeriod(period) and
                        ownedBy(ownerId)
            }) {
                it[lockExpiresAt] = expiresAt
                it[updatedAt] = CurrentDateTime
            }
        }
    }

    override suspend fun getLastProcessedAt(
        orchestratorName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): Instant? = newSuspendedTransaction(db = database) {
        runsTable
            .selectAll()
            .where { sameOrchestrator(orchestratorName) and sameContext(context) and samePeriod(period) }
            .singleOrNull()
            ?.get(runsTable.lastProcessedAt)
    }

    override suspend fun markFinishedAndRelease(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        lastProcessedAt: Instant?,
        period: EtlPeriod,
    ) {
        newSuspendedTransaction(db = database) {
            runsTable.update(where = {
                sameOrchestrator(orchestratorName) and
                        sameContext(context) and
                        samePeriod(period) and
                        ownedBy(ownerId)
            }) {
                it[status] = EtlRunStatus.IDLE.name
                it[runsTable.lastFinishedAt] = CurrentTimestamp
                if (lastProcessedAt != null) {
                    it[runsTable.lastProcessedAt] = lastProcessedAt
                }
                it[lockOwner] = null
                it[lockExpiresAt] = null
                it[updatedAt] = CurrentDateTime
            }
        }
    }

    private fun hasOverlappingActiveLock(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        period: EtlPeriod,
    ): Boolean = runsTable
        .selectAll()
        .where {
            sameOrchestrator(orchestratorName) and
                    sameContext(context) and
                    // exclude the incremental/unbounded lane
                    notSentinelPeriod() and
                    // exclude this exact period (re-acquire is handled by the upsert guard)
                    ((runsTable.periodFrom neq period.storedFrom) or (runsTable.periodTo neq period.storedTo)) and
                    // day ranges overlap
                    (runsTable.periodFrom lessEq period.storedTo) and
                    (runsTable.periodTo greaterEq period.storedFrom) and
                    // held by someone else and not expired
                    runsTable.lockOwner.isNotNull() and
                    (runsTable.lockOwner neq ownerId) and
                    (runsTable.status eq EtlRunStatus.RUNNING.name) and
                    (runsTable.lockExpiresAt greaterEq CurrentTimestamp)
        }
        .limit(1)
        .any()

    private fun advisoryLockKey(orchestratorName: String, context: EtlContext): Long {
        val key = listOf(
            orchestratorName,
            context.groupId,
            context.appId.orEmpty(),
            context.instanceId.orEmpty(),
            context.buildId.orEmpty(),
            context.testSessionId.orEmpty(),
            context.testDefinitionId.orEmpty(),
            context.testLaunchId.orEmpty(),
        ).joinToString("\u0000")
        return key.hashCode().toLong()
    }

    private fun sameOrchestrator(
        orchestratorName: String,
    ): Op<Boolean> = runsTable.orchestratorName eq orchestratorName

    private fun ownedBy(
        ownerId: String,
    ): Op<Boolean> = runsTable.lockOwner eq ownerId

    private fun freeOrOwnedBy(
        ownerId: String,
    ): Op<Boolean> = runsTable.lockOwner.isNull() or
            runsTable.lockExpiresAt.less(CurrentTimestamp) or
            ownedBy(ownerId)

    private fun samePeriod(
        period: EtlPeriod,
    ): Op<Boolean> =
        (runsTable.periodFrom eq period.storedFrom) and (runsTable.periodTo eq period.storedTo)

    private fun notSentinelPeriod(): Op<Boolean> =
        (runsTable.periodFrom neq EtlPeriod.SENTINEL_FROM) or (runsTable.periodTo neq EtlPeriod.SENTINEL_TO)

    private fun sameContext(
        context: EtlContext,
    ): Op<Boolean> =
        (runsTable.groupId eq context.groupId) and
                (runsTable.appId eq context.appId.orEmpty()) and
                (runsTable.instanceId eq context.instanceId.orEmpty()) and
                (runsTable.buildId eq context.buildId.orEmpty()) and
                (runsTable.testSessionId eq context.testSessionId.orEmpty()) and
                (runsTable.testDefinitionId eq context.testDefinitionId.orEmpty()) and
                (runsTable.testLaunchId eq context.testLaunchId.orEmpty())

}