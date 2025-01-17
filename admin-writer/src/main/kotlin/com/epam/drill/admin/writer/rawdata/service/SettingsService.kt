package com.epam.drill.admin.writer.rawdata.service

import com.epam.drill.admin.writer.rawdata.route.payload.GroupSettingsPayload
import com.epam.drill.admin.writer.rawdata.views.GroupSettingsView

interface SettingsService {
    fun getGroupSettings(groupId: String): GroupSettingsView
    fun saveGroupSettings(groupId: String, payload: GroupSettingsPayload)
    fun clearGroupSettings(groupId: String)
}