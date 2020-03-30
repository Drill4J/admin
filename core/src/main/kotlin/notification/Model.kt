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
    val prevId: String,
    val buildDiff: BuildDiff,
    val recommendations: List<String>
)

@Serializable
data class BuildDiff(
    @Transient
    val modBody: Int = 0,
    @Transient
    val modDesc: Int = 0,
    @Transient
    val modName: Int = 0,

    val new: Int,
    val deleted: Int
) {
    val modified = modBody + modDesc + modName
}
