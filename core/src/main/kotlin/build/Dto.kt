package com.epam.drill.admin.build

import kotlinx.serialization.*

@Serializable
data class BuildSummaryDto(
    val buildVersion: String = "",
    val detectedAt: Long = 0L,
    val totalMethods: Int = 0,
    val newMethods: Int = 0,
    val modifiedMethods: Int = 0,
    val unaffectedMethods: Int = 0,
    val deletedMethods: Int = 0
)
