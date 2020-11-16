package com.epam.drill.admin.notification

import kotlinx.serialization.*

@Serializable
data class Notification(
    val id: String,
    val agentId: String,
    val createdAt: Long,
    val type: NotificationType,
    val read: Boolean = false,
    @ContextualSerialization val message: Any
)

enum class NotificationType {
    BUILD
}

@Serializable
data class NewBuildArrivedMessage(
    val currentId: String,
    val recommendations: Set<String> = emptySet(),
    @ContextualSerialization val buildInfo: Any? = null
)
