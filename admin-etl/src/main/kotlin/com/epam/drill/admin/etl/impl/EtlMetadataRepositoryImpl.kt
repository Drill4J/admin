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
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert

class EtlMetadataRepositoryImpl(
    private val database: Database,
    dbSchema: String = "etl",
    metadataTableName: String = "etl_metadata"
) : EtlMetadataRepository {
    private val metadataTable: EtlMetadataTable = EtlMetadataTable("$dbSchema.$metadataTableName")

    override suspend fun getMetadata(pipelineName: String): EtlMetadata? = newSuspendedTransaction(db = database) {
        metadataTable.selectAll().where { metadataTable.id eq pipelineName }
            .map {
                EtlMetadata(
                    pipelineName = it[metadataTable.id].value,
                    lastProcessedAt = it[metadataTable.lastProcessedAt],
                    lastRunAt = it[metadataTable.lastRunAt],
                    duration = it[metadataTable.duration],
                    status = EtlStatus.valueOf(it[metadataTable.status]),
                    errorMessage = it[metadataTable.errorMessage],
                    rowsProcessed = it[metadataTable.rowsProcessed]
                )
            }.singleOrNull()
    }

    override suspend fun saveMetadata(metadata: EtlMetadata): Unit = newSuspendedTransaction(db = database) {
        metadataTable.upsert {
            it[id] = metadata.pipelineName
            it[lastProcessedAt] = metadata.lastProcessedAt
            it[lastRunAt] = metadata.lastRunAt
            it[duration] = metadata.duration
            it[status] = metadata.status.name
            it[errorMessage] = metadata.errorMessage
            it[rowsProcessed] = metadata.rowsProcessed
        }
    }
}