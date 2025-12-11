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
import com.epam.drill.admin.etl.EtlStatus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
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

class EtlMetadataRepositoryImpl(
    private val database: Database,
    dbSchema: String = "etl",
    metadataTableName: String = "etl_metadata"
) : EtlMetadataRepository {
    private val metadataTable: EtlMetadataTable = EtlMetadataTable("$dbSchema.$metadataTableName")

    override suspend fun getAllMetadata(groupId: String): List<EtlMetadata> {
        return newSuspendedTransaction(db = database) {
            metadataTable.selectAll()
                .andWhere { metadataTable.groupId eq groupId }
                .map(::mapMetadata)
        }
    }

    override suspend fun getAllMetadataByExtractor(
        groupId: String,
        pipelineName: String,
        extractorName: String
    ): List<EtlMetadata> = newSuspendedTransaction(db = database) {
        metadataTable.selectAll()
            .andWhere { metadataTable.groupId eq groupId }
            .andWhere { metadataTable.pipelineName eq pipelineName }
            .andWhere { metadataTable.extractorName eq extractorName }
            .orderBy(metadataTable.lastProcessedAt to SortOrder.ASC)
            .map(::mapMetadata)
    }

    override suspend fun getMetadata(
        groupId: String,
        pipelineName: String,
        extractorName: String,
        loaderName: String
    ): EtlMetadata? = newSuspendedTransaction(db = database) {
        metadataTable.selectAll()
            .andWhere { metadataTable.groupId eq groupId }
            .andWhere { metadataTable.pipelineName eq pipelineName }
            .andWhere { metadataTable.extractorName eq extractorName }
            .andWhere { metadataTable.loaderName eq loaderName }
            .map(::mapMetadata)
            .singleOrNull()
    }

    override suspend fun saveMetadata(metadata: EtlMetadata): Unit = newSuspendedTransaction(db = database) {
        metadataTable.upsert(
            onUpdateExclude = listOf(
                metadataTable.duration,
                metadataTable.rowsProcessed,
                metadataTable.createdAt
            ),
        ) {
            it[groupId] = metadata.groupId
            it[pipelineName] = metadata.pipelineName
            it[extractorName] = metadata.extractorName
            it[loaderName] = metadata.loaderName
            it[status] = metadata.status.name
            it[lastProcessedAt] = metadata.lastProcessedAt
            it[lastRunAt] = metadata.lastRunAt
            it[lastDuration] = metadata.lastDuration
            it[lastRowsProcessed] = metadata.lastRowsProcessed
            it[errorMessage] = metadata.errorMessage
            it[updatedAt] = CurrentDateTime
        }
    }

    override suspend fun accumulateMetadata(metadata: EtlMetadata): Unit = newSuspendedTransaction(db = database) {
        metadataTable.update(where = {
            (metadataTable.groupId eq metadata.groupId) and
                    (metadataTable.pipelineName eq metadata.pipelineName) and
                    (metadataTable.extractorName eq metadata.extractorName) and
                    (metadataTable.loaderName eq metadata.loaderName)
        }) {
            it[status] = metadata.status.name
            it[lastProcessedAt] = metadata.lastProcessedAt
            it[lastRunAt] = metadata.lastRunAt
            it[lastDuration] = lastDuration + metadata.lastDuration
            it[lastRowsProcessed] = lastRowsProcessed + metadata.lastRowsProcessed
            it[duration] = duration + metadata.lastDuration
            it[rowsProcessed] = rowsProcessed + metadata.lastRowsProcessed
            it[errorMessage] = metadata.errorMessage
            it[updatedAt] = CurrentDateTime
        }
    }

    override suspend fun deleteMetadataByPipeline(groupId: String, pipelineName: String) {
        newSuspendedTransaction(db = database) {
            metadataTable.deleteWhere { 
                (metadataTable.groupId eq groupId) and (metadataTable.pipelineName eq pipelineName)
            }
        }
    }

    override suspend fun accumulateMetadataDurationByExtractor(
        groupId: String,
        pipelineName: String,
        extractorName: String,
        duration: Long
    ) {
        newSuspendedTransaction(db = database) {
            metadataTable.update(where = {
                (metadataTable.groupId eq groupId) and
                (metadataTable.pipelineName eq pipelineName) and 
                (metadataTable.extractorName eq extractorName)
            }) {
                it[lastDuration] = lastDuration + duration
                it[metadataTable.duration] = metadataTable.duration + duration
                it[metadataTable.updatedAt] = CurrentDateTime
            }
        }
    }

    private fun mapMetadata(row: ResultRow): EtlMetadata = EtlMetadata(
        groupId = row[metadataTable.groupId],
        pipelineName = row[metadataTable.pipelineName],
        extractorName = row[metadataTable.extractorName],
        loaderName = row[metadataTable.loaderName],
        lastProcessedAt = row[metadataTable.lastProcessedAt],
        lastRunAt = row[metadataTable.lastRunAt],
        lastDuration = row[metadataTable.lastDuration],
        lastRowsProcessed = row[metadataTable.lastRowsProcessed],
        status = EtlStatus.valueOf(row[metadataTable.status]),
        errorMessage = row[metadataTable.errorMessage],
    )
}