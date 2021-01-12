package com.epam.drill.admin.build

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class BuildSummaryDto(
    val buildVersion: String = "",
    val detectedAt: Long = 0L,
    val summary: JsonElement = JsonNull
)
