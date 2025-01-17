package com.epam.drill.admin.writer.rawdata.service.impl

import com.epam.drill.admin.writer.rawdata.entity.GroupSettings
import com.epam.drill.admin.writer.rawdata.repository.GroupSettingsRepository
import com.epam.drill.admin.writer.rawdata.route.payload.GroupSettingsPayload
import com.epam.drill.admin.writer.rawdata.service.SettingsService
import com.epam.drill.admin.writer.rawdata.views.GroupSettingsView
import org.jetbrains.exposed.sql.transactions.transaction

class SettingsServiceImpl(
    private val groupSettingsRepository: GroupSettingsRepository
) : SettingsService {

    override fun getGroupSettings(groupId: String) = transaction {
        groupSettingsRepository.getByGroupId(groupId).let { settings ->
            GroupSettingsView(
                retentionPeriodDays = settings?.retentionPeriodDays
            )
        }
    }


    override fun saveGroupSettings(groupId: String, payload: GroupSettingsPayload) = transaction {
        groupSettingsRepository.save(
            GroupSettings(
                groupId = groupId,
                retentionPeriodDays = payload.retentionPeriodDays
            )
        )
    }

    override fun clearGroupSettings(groupId: String) = transaction {
        groupSettingsRepository.deleteByGroupId(groupId)
    }
}