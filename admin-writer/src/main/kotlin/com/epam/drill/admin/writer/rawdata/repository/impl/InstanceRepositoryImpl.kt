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
package com.epam.drill.admin.writer.rawdata.repository.impl

import com.epam.drill.admin.writer.rawdata.entity.Instance
import com.epam.drill.admin.writer.rawdata.entity.InstanceHeartbeat
import com.epam.drill.admin.writer.rawdata.repository.InstanceRepository
import com.epam.drill.admin.writer.rawdata.route.payload.AgentHeartbeatStatus
import com.epam.drill.admin.writer.rawdata.table.InstanceTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate

class InstanceRepositoryImpl : InstanceRepository {

    override suspend fun create(instance: Instance) {
        InstanceTable.upsert(
            onUpdateExclude = listOf(
                InstanceTable.status,
                InstanceTable.lastHeartbeatAt,
            )
        ) {
            it[id] = instance.id
            it[groupId] = instance.groupId
            it[appId] = instance.appId
            it[buildId] = instance.buildId
            it[envId] = instance.envId
            it[lastHeartbeatAt] = CurrentDateTime
            it[status] = AgentHeartbeatStatus.RUNNING.name
        }
    }

    override suspend fun updateHeartbeat(instance: InstanceHeartbeat) {
        InstanceTable.update(where = {
            (InstanceTable.groupId eq instance.groupId) and
                    (InstanceTable.appId eq instance.appId) and
                    (InstanceTable.id eq instance.id)
        }) {
            it[lastHeartbeatAt] = CurrentDateTime
            it[status] = instance.status.name
        }
    }

    override suspend fun deleteAllCreatedBefore(groupId: String, createdBefore: LocalDate) {
        InstanceTable.deleteWhere { (InstanceTable.groupId eq groupId) and (InstanceTable.createdAt less createdBefore.atStartOfDay()) }
    }

    override suspend fun deleteAllByBuildId(groupId: String, appId: String, buildId: String) {
        InstanceTable.deleteWhere {
            (InstanceTable.groupId eq groupId) and
                    (InstanceTable.appId eq appId) and
                    (InstanceTable.buildId eq buildId)
        }
    }
}