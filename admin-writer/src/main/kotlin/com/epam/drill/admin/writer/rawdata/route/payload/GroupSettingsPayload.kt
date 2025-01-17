package com.epam.drill.admin.writer.rawdata.route.payload

import kotlinx.serialization.Serializable

@Serializable
class GroupSettingsPayload(
    val retentionPeriodDays: Int?
)