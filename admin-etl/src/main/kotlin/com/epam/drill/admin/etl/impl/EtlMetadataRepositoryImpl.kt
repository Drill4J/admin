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

import com.epam.drill.admin.etl.table.EtlMetadataTable
import com.epam.drill.admin.etl.EtlMetadata
import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlStatus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.Instant

class EtlMetadataRepositoryImpl(
    private val database: Database,
    dbSchema: String = "etl",
    metadataTableName: String = "etl_metadata"
) : EtlMetadataRepository {
    private val metadataTable: EtlMetadataTable = EtlMetadataTable("$dbSchema.$metadataTableName")

    override suspend fun getAllMetadata(context: EtlContext): List<EtlMetadata> {
        return newSuspendedTransaction(db = database) {
            metadataTable.selectAll()
                .andWhere { metadataTableHas(context) }
                .map(::mapMetadata)
        }
    }

    override suspend fun getMetadata(
        context: EtlContext,
        pipelineName: String
    ): EtlMetadata? = newSuspendedTransaction(db = database) {
        metadataTable.selectAll()
            .andWhere { metadataTableHas(context) }
            .andWhere { metadataTable.pipelineName eq pipelineName }
            .map(::mapMetadata)
            .singleOrNull()
    }

    override suspend fun saveMetadata(context: EtlContext, metadata: EtlMetadata): Unit = newSuspendedTransaction(db = database) {
        metadataTable.upsert(
            onUpdateExclude = listOf(
                metadataTable.loadDuration,
                metadataTable.extractDuration,
                metadataTable.rowsProcessed,
                metadataTable.lastProcessedAt,
                metadataTable.createdAt
            ),
        ) {
            it[pipelineName] = metadata.pipelineName
            it[extractorName] = metadata.extractorName
            it[loaderName] = metadata.loaderName

            it[status] = metadata.status.name
            it[lastProcessedAt] = metadata.lastProcessedAt
            it[lastRunAt] = metadata.lastRunAt
            it[lastLoadDuration] = metadata.lastLoadDuration
            it[lastExtractDuration] = metadata.lastExtractDuration
            it[lastRowsProcessed] = metadata.lastRowsProcessed
            it[errorMessage] = metadata.errorMessage

            it[groupId] = context.groupId
            it[appId] = context.appId ?: ""
            it[buildId] = context.buildId ?: ""
            it[instanceId] = context.instanceId ?: ""
            it[testSessionId] = context.testSessionId ?: ""
            it[testDefinitionId] = context.testDefinitionId ?: ""
            it[testLaunchId] = context.testLaunchId ?: ""

            it[updatedAt] = CurrentDateTime
        }
    }

    override suspend fun accumulateMetadataByLoader(
        context: EtlContext,
        pipelineName: String,
        lastProcessedAt: Instant?,
        status: EtlStatus?,
        loadDuration: Long,
        rowsProcessed: Long,
        errorMessage: String?
    ) {
        newSuspendedTransaction(db = database) {
            metadataTable.update(where = {
                metadataTableHas(context) and (metadataTable.pipelineName eq pipelineName)

            }) {
                if (errorMessage != null) {
                    it[metadataTable.errorMessage] = errorMessage
                    it[metadataTable.status] = EtlStatus.FAILED.name
                }
                if (status != null) {
                    it[metadataTable.status] = status.name
                }
                if (lastProcessedAt != null) {
                    it[metadataTable.lastProcessedAt] = lastProcessedAt
                }
                it[metadataTable.lastLoadDuration] = metadataTable.lastLoadDuration + loadDuration
                it[metadataTable.lastRowsProcessed] = metadataTable.lastRowsProcessed + rowsProcessed
                it[metadataTable.loadDuration] = metadataTable.loadDuration + loadDuration
                it[metadataTable.rowsProcessed] = metadataTable.rowsProcessed + rowsProcessed
                it[updatedAt] = CurrentDateTime
            }
        }
    }

    override suspend fun deleteMetadataByPipeline(context: EtlContext, pipelineName: String) {
        newSuspendedTransaction(db = database) {
            metadataTable.deleteWhere {
                metadataTableHas(context) and (metadataTable.pipelineName eq pipelineName)
            }
        }
    }

    override suspend fun accumulateMetadataByExtractor(
        context: EtlContext,
        pipelineName: String,
        status: EtlStatus?,
        extractDuration: Long,
        errorMessage: String?
    ) {
        newSuspendedTransaction(db = database) {
            metadataTable.update(where = {
                metadataTableHas(context) and
                        (metadataTable.pipelineName eq pipelineName) and
                        (status?.let { metadataTable.status neq EtlStatus.FAILED.name } ?: Op.TRUE)
            }) {
                if (errorMessage != null) {
                    it[metadataTable.errorMessage] = errorMessage
                    it[metadataTable.status] = EtlStatus.FAILED.name
                }
                if (status != null) {
                    it[metadataTable.status] = status.name
                }
                it[metadataTable.lastExtractDuration] = metadataTable.lastExtractDuration + extractDuration
                it[metadataTable.extractDuration] = metadataTable.extractDuration + extractDuration
                it[metadataTable.updatedAt] = CurrentDateTime
            }
        }
    }

    private fun mapMetadata(row: ResultRow): EtlMetadata = EtlMetadata(
        pipelineName = row[metadataTable.pipelineName],
        extractorName = row[metadataTable.extractorName],
        loaderName = row[metadataTable.loaderName],
        lastProcessedAt = row[metadataTable.lastProcessedAt],
        lastRunAt = row[metadataTable.lastRunAt],
        lastLoadDuration = row[metadataTable.lastLoadDuration],
        lastExtractDuration = row[metadataTable.lastExtractDuration],
        lastRowsProcessed = row[metadataTable.lastRowsProcessed],
        status = EtlStatus.valueOf(row[metadataTable.status]),
        errorMessage = row[metadataTable.errorMessage],
    )
    
    private fun metadataTableHas(context: EtlContext): Op<Boolean> {
        return (metadataTable.groupId eq context.groupId) and
                (metadataTable.appId eq (context.appId ?: "")) and
                (metadataTable.buildId eq (context.buildId ?: "")) and
                (metadataTable.instanceId eq (context.instanceId ?: "")) and
                (metadataTable.testSessionId eq (context.testSessionId ?: "")) and
                (metadataTable.testDefinitionId eq (context.testDefinitionId ?: "")) and
                (metadataTable.testLaunchId eq (context.testLaunchId ?: ""))
    }
}