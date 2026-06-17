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

class EtlRunsTable(tableName: String) : Table(tableName) {
    val orchestratorName = varchar("orchestrator_name", 225)
    val groupId = varchar("group_id", 225)
    val appId = varchar("app_id", 225)
    val instanceId = varchar("instance_id", 225)
    val buildId = varchar("build_id", 225)
    val testSessionId = varchar("test_session_id", 225)
    val testDefinitionId = varchar("test_definition_id", 225)
    val testLaunchId = varchar("test_launch_id", 225)
    override val primaryKey = PrimaryKey(
        orchestratorName, groupId, appId, instanceId, buildId,
        testSessionId, testDefinitionId, testLaunchId
    )

    val runsCount = long("runs_count").default(0L)
    val status = varchar("status", 50)
    val lastStartedAt = timestamp("last_started_at").nullable()
    val lastFinishedAt = timestamp("last_finished_at").nullable()
    val lastProcessedAt = timestamp("last_processed_at").nullable()
    val lockOwner = varchar("lock_owner", 255).nullable()
    val lockExpiresAt = timestamp("lock_expires_at").nullable()

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}
