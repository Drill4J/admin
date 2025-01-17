package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.GroupSettings

interface GroupSettingsRepository {
    fun getAll(): List<GroupSettings>
    fun getByGroupId(groupId: String): GroupSettings?
    fun save(settings: GroupSettings)
    fun deleteByGroupId(groupId: String)
}