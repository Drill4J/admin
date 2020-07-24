package com.epam.drill.admin.build

import kotlinx.serialization.*

@Serializable
data class BuildSummaryDto(
    val buildVersion: String = "",
    val detectedAt: Long = 0L,
    @ContextualSerialization val summary: Any? = null
)
