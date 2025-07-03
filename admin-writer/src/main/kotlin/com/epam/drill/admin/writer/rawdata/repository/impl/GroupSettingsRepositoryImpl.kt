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

import com.epam.drill.admin.writer.rawdata.entity.GroupSettings
import com.epam.drill.admin.writer.rawdata.repository.GroupSettingsRepository
import com.epam.drill.admin.writer.rawdata.table.GroupSettingsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class GroupSettingsRepositoryImpl : GroupSettingsRepository {
    override fun getAll(): List<GroupSettings> {
        return GroupSettingsTable.selectAll().map {
            GroupSettings(
                groupId = it[GroupSettingsTable.id].value,
                retentionPeriodDays = it[GroupSettingsTable.retentionPeriodDays],
                metricsPeriodDays = it[GroupSettingsTable.metricsPeriodDays],
            )
        }
    }

    override fun getByGroupId(groupId: String): GroupSettings? {
        return GroupSettingsTable.selectAll()
            .where { GroupSettingsTable.id eq groupId }
            .limit(1).singleOrNull()?.let {
                GroupSettings(
                    groupId = it[GroupSettingsTable.id].value,
                    retentionPeriodDays = it[GroupSettingsTable.retentionPeriodDays],
                    metricsPeriodDays = it[GroupSettingsTable.metricsPeriodDays],
                )
            }
    }

    override fun save(settings: GroupSettings) {
        GroupSettingsTable.upsert {
            it[id] = settings.groupId
            it[retentionPeriodDays] = settings.retentionPeriodDays
            it[metricsPeriodDays] = settings.metricsPeriodDays
        }
    }

    override fun deleteByGroupId(groupId: String) {
        GroupSettingsTable.deleteWhere { GroupSettingsTable.id eq groupId }
    }
}