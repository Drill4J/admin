package com.epam.drill.dataclasses

import kotlinx.serialization.*

@Serializable
data class Notification(
    val id: String,
    val agentId: String,
    val agentName: String,
    val date: Long,
    val status: NotificationStatus,
    val type: NotificationType,
    val message: String
)

enum class NotificationStatus {
    UNREAD,
    READ
}

enum class NotificationType {
    BUILD
}

@Serializable
data class NotificationId(val notificationId: String)

@Serializable
data class NewBuildArrivedMessage(
    val current: String,
    val prev: String,
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
    val del: Int
) {
    val mod = modBody + modDesc + modName
}
