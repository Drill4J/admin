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
                retentionPeriodDays = it[GroupSettingsTable.retentionPeriodDays]
            )
        }
    }

    override fun getByGroupId(groupId: String): GroupSettings? {
        return GroupSettingsTable.selectAll()
            .where { GroupSettingsTable.id eq groupId }
            .limit(1).singleOrNull()?.let {
                GroupSettings(
                    groupId = it[GroupSettingsTable.id].value,
                    retentionPeriodDays = it[GroupSettingsTable.retentionPeriodDays]
                )
            }
    }

    override fun save(settings: GroupSettings) {
        GroupSettingsTable.upsert {
            it[id] = settings.groupId
            it[retentionPeriodDays] = settings.retentionPeriodDays
        }
    }

    override fun deleteByGroupId(groupId: String) {
        GroupSettingsTable.deleteWhere { GroupSettingsTable.id eq groupId }
    }
}