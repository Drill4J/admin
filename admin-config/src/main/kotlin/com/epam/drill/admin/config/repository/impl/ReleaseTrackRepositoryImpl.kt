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

import com.epam.drill.admin.config.repository.ReleaseTrackRepository
import com.epam.drill.admin.config.repository.ReleaseTrackRow
import com.epam.drill.admin.config.table.ReleaseTrackTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll

class ReleaseTrackRepositoryImpl : ReleaseTrackRepository {

    override fun existsByTrackId(groupId: String, trackId: String): Boolean {
        return ReleaseTrackTable.selectAll()
            .where {
                (ReleaseTrackTable.groupId eq groupId) and
                        (ReleaseTrackTable.trackId.lowerCase() eq trackId.lowercase())
            }
            .count() > 0
    }

    override fun insert(groupId: String, trackId: String) {
        ReleaseTrackTable.insert {
            it[this.groupId] = groupId
            it[this.trackId] = trackId
        }
    }

    override fun getList(groupId: String): List<ReleaseTrackRow> {
        return ReleaseTrackTable.selectAll()
            .where { ReleaseTrackTable.groupId eq groupId }
            .orderBy(ReleaseTrackTable.createdAt, SortOrder.ASC)
            .map { row ->
                ReleaseTrackRow(
                    trackId = row[ReleaseTrackTable.trackId],
                    createdAt = row[ReleaseTrackTable.createdAt]
                )
            }
    }

    override fun getListLight(groupId: String): List<ReleaseTrackRow> = getList(groupId)
}
