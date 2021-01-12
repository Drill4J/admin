package com.epam.drill.admin.notification

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class Notification(
    val id: String,
    val agentId: String,
    val createdAt: Long,
    val type: NotificationType,
    val read: Boolean = false,
    val message: JsonElement
)

enum class NotificationType {
    BUILD
}

@Serializable
data class NewBuildArrivedMessage(
    val currentId: String,
    val recommendations: Set<String> = emptySet(),
    val buildInfo: JsonElement = JsonNull
)
