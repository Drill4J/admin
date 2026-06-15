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
import com.epam.drill.admin.etl.EtlRunStatus
import com.epam.drill.admin.etl.EtlRunsRepository
import com.epam.drill.admin.etl.table.EtlRunsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
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
    ): Boolean = newSuspendedTransaction(db = database) {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(leaseSeconds)
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
    ) {
        val expiresAt = Instant.now().plusSeconds(leaseSeconds)
        newSuspendedTransaction(db = database) {
            runsTable.update(where = {
                sameOrchestrator(orchestratorName) and
                        sameContext(context) and
                        ownedBy(ownerId)
            }) {
                it[lockExpiresAt] = expiresAt
                it[updatedAt] = CurrentDateTime
            }
        }
    }

    override suspend fun markFinishedAndRelease(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
    ) {
        newSuspendedTransaction(db = database) {
            runsTable.update(where = {
                sameOrchestrator(orchestratorName) and
                        sameContext(context) and
                        ownedBy(ownerId)
            }) {
                it[status] = EtlRunStatus.IDLE.name
                it[lastFinishedAt] = CurrentTimestamp
                it[lockOwner] = null
                it[lockExpiresAt] = null
                it[updatedAt] = CurrentDateTime
            }
        }
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