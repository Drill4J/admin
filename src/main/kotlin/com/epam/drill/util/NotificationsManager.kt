package com.epam.drill.util

import com.epam.drill.dataclasses.*
import java.util.concurrent.*

class NotificationsManager{
    private val notifications = ConcurrentHashMap<String, Notification>()

    val allNotifications
        get() = notifications.values

    fun save(agentId: String, agentName: String, type: NotificationType, message: String) {
        val id = java.util.UUID.randomUUID().toString()
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

    fun delete(id: String): Boolean = notifications[id]?.let{
        notifications.remove(id) != null
    } ?: false

    fun buildArrivedMessage(buildVersion: String) = "Build $buildVersion arrived"
}