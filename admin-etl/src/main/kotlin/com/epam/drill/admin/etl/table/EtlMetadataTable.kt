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
package com.epam.drill.admin.etl.table

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.timestamp

class EtlMetadataTable(tableName: String) : IdTable<String>(tableName) {
    override val id: Column<EntityID<String>> = varchar("pipeline_name", 225).entityId()
    override val primaryKey = PrimaryKey(id)

    val status = varchar("status", 50)
    val lastProcessedAt = timestamp("last_processed_at")
    val lastRunAt = timestamp("last_run_at")
    val duration = long("duration")
    val rowsProcessed = integer("rows_processed")
    val errorMessage = text("error_message").nullable()
}