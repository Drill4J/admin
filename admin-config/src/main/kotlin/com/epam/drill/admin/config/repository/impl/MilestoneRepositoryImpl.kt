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
package com.epam.drill.admin.config.repository.impl

import com.epam.drill.admin.config.model.MilestoneAppInput
import com.epam.drill.admin.config.repository.MilestoneRepository
import com.epam.drill.admin.config.repository.MilestoneRow
import com.epam.drill.admin.config.table.MilestoneAppTable
import com.epam.drill.admin.config.table.MilestoneTable
import com.epam.drill.admin.config.table.ReleaseTrackAppTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class MilestoneRepositoryImpl : MilestoneRepository {

    override fun getList(groupId: String, trackId: String): List<MilestoneRow> {
        return MilestoneTable.selectAll()
            .where {
                (MilestoneTable.groupId eq groupId) and (MilestoneTable.trackId eq trackId)
            }
            .orderBy(MilestoneTable.createdAt, SortOrder.DESC)
            .map { row ->
                MilestoneRow(
                    milestoneId = row[MilestoneTable.milestoneId],
                    createdAt = row[MilestoneTable.createdAt]
                )
            }
    }

    override fun getTrackAppIds(groupId: String, trackId: String): Set<String> {
        return ReleaseTrackAppTable.selectAll()
            .where {
                (ReleaseTrackAppTable.groupId eq groupId) and (ReleaseTrackAppTable.trackId eq trackId)
            }
            .map { it[ReleaseTrackAppTable.appId] }
            .toSet()
    }

    override fun upsertMilestone(groupId: String, trackId: String, milestoneId: String) {
        MilestoneTable.upsert(
            MilestoneTable.groupId,
            MilestoneTable.trackId,
            MilestoneTable.milestoneId,
            onUpdateExclude = listOf(MilestoneTable.createdAt)
        ) {
            it[this.groupId] = groupId
            it[this.trackId] = trackId
            it[this.milestoneId] = milestoneId
        }
    }

    override fun deleteApps(groupId: String, trackId: String, milestoneId: String) {
        MilestoneAppTable.deleteWhere {
            (MilestoneAppTable.groupId eq groupId) and
                    (MilestoneAppTable.trackId eq trackId) and
                    (MilestoneAppTable.milestoneId eq milestoneId)
        }
    }

    override fun insertApps(groupId: String, trackId: String, milestoneId: String, apps: List<MilestoneAppInput>) {
        MilestoneAppTable.batchInsert(apps) { app ->
            this[MilestoneAppTable.groupId] = groupId
            this[MilestoneAppTable.trackId] = trackId
            this[MilestoneAppTable.milestoneId] = milestoneId
            this[MilestoneAppTable.appId] = app.appId
            this[MilestoneAppTable.buildId] = app.buildId
        }
    }
}
