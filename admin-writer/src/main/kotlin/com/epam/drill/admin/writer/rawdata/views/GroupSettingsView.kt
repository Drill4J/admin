package com.epam.drill.admin.writer.rawdata.views

import kotlinx.serialization.Serializable

@Serializable
class GroupSettingsView(
    val retentionPeriodDays: Int? = null
)