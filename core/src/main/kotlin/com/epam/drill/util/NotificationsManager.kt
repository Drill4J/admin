package com.epam.drill.util

import com.epam.drill.dataclasses.*
import mu.*
import java.util.concurrent.*

class NotificationsManager {
    private val notifications = ConcurrentHashMap<String, Notification>()
    private val logger = KotlinLogging.logger {}

    val allNotifications
        get() = notifications.values

    fun save(agentId: String, agentName: String, type: NotificationType, message: String) {
        val id = java.util.UUID.randomUUID().toString()
        logger.info { "New notification type $type with $id associated with agent $agentId. Message: $message" }
        notifications[id] = Notification(
            id,
            agentId,
            agentName,
            System.currentTimeMillis(),
            NotificationStatus.UNREAD,
            type,
            message
        )
    }

    fun readAll() {
        allNotifications.forEach {
            notifications[it.id] = it.copy(status = NotificationStatus.READ)
        }
    }

    fun read(id: String): Boolean = notifications[id]?.let {
        val readNotification = it.copy(status = NotificationStatus.READ)
        notifications[id] = readNotification
        notifications[id]!!.id == readNotification.id
    } ?: false

    fun deleteAll() {
        notifications.clear()
    }

    fun delete(id: String): Boolean = notifications[id]?.let {
        notifications.remove(id) != null
    } ?: false
}
