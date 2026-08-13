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

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestamp

class EtlJobsTable(tableName: String) : Table(tableName) {
    val etlName = varchar("etl_name", 225)
    val groupId = varchar("group_id", 225)
    val appId = varchar("app_id", 225)
    val buildId = varchar("build_id", 225)
    val testSessionId = varchar("test_session_id", 225)
    val testDefinitionId = varchar("test_definition_id", 225)
    val period = registerColumn("period", DateRangeColumnType())

    val status = varchar("status", 50)
    val processedUntilTimestamp = timestamp("processed_until_timestamp")
    val errorMessage = text("error_message").nullable()

    val workerId = varchar("worker_id", 255).nullable()
    val lockExpiresAt = timestamp("lock_expires_at").nullable()
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}