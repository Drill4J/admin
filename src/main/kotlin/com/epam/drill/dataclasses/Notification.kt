package com.epam.drill.dataclasses

import kotlinx.serialization.Serializable

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

enum class NotificationStatus{
    UNREAD,
    READ
}

enum class NotificationType{
    BUILD
}

@Serializable
data class NotificationId(val notificationId: String)