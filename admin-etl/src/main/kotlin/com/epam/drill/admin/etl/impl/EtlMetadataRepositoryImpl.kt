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
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.time.Instant

class EtlMetadataRepositoryImpl(
    private val database: Database,
    dbSchema: String = "etl",
    metadataTableName: String = "etl_metadata"
) : EtlMetadataRepository {
    private val metadataTable: EtlMetadataTable = EtlMetadataTable("$dbSchema.$metadataTableName")


    override suspend fun getAllMetadataByExtractor(
        pipelineName: String,
        extractorName: String
    ): List<EtlMetadata> = newSuspendedTransaction(db = database) {
        metadataTable.selectAll()
            .andWhere { metadataTable.pipelineName eq pipelineName }
            .andWhere { metadataTable.extractorName eq extractorName }
            .orderBy(metadataTable.lastProcessedAt to SortOrder.ASC)
            .map(::mapMetadata)
    }

    override suspend fun getMetadata(
        pipelineName: String,
        extractorName: String,
        loaderName: String
    ): EtlMetadata? = newSuspendedTransaction(db = database) {
        metadataTable.selectAll()
            .andWhere { metadataTable.pipelineName eq pipelineName }
            .andWhere { metadataTable.extractorName eq extractorName }
            .andWhere { metadataTable.loaderName eq loaderName }
            .map(::mapMetadata)
            .singleOrNull()
    }

    override suspend fun saveMetadata(metadata: EtlMetadata): Unit = newSuspendedTransaction(db = database) {
        metadataTable.upsert {
            it[pipelineName] = metadata.pipelineName
            it[extractorName] = metadata.extractorName
            it[loaderName] = metadata.loaderName
            it[status] = metadata.status.name
            it[lastProcessedAt] = metadata.lastProcessedAt
            it[lastRunAt] = metadata.lastRunAt
            it[duration] = metadata.duration
            it[rowsProcessed] = metadata.rowsProcessed
            it[errorMessage] = metadata.errorMessage
        }
    }

    private fun mapMetadata(row: ResultRow): EtlMetadata = EtlMetadata(
        pipelineName = row[metadataTable.pipelineName],
        extractorName = row[metadataTable.extractorName],
        loaderName = row[metadataTable.loaderName],
        lastProcessedAt = row[metadataTable.lastProcessedAt],
        lastRunAt = row[metadataTable.lastRunAt],
        duration = row[metadataTable.duration],
        status = EtlStatus.valueOf(row[metadataTable.status]),
        errorMessage = row[metadataTable.errorMessage],
        rowsProcessed = row[metadataTable.rowsProcessed]
    )
}