package com.epam.drill.admin.notifications

import com.epam.drill.admin.notification.*
import kotlin.test.*

class NotificationsTest {
    private val agentId = "test-agent"

    @Test
    fun `plus - two identical notifications`() {
        var notifications = Notifications()
        val notification = Notification(
            id = "some-id",
            agentId = agentId,
            createdAt = System.currentTimeMillis(),
            type = NotificationType.BUILD,
            message = "some-message"
        )
        notifications = notifications.plus(notification)
        notifications = notifications.plus(notification)
        assertEquals(1, notifications.valuesDesc.size)
    }

    @Test
    fun `plus - asc and desc must contain the same elements`() {
        var notifications = Notifications()
        val notification = Notification(
            id = "some-id",
            agentId = agentId,
            createdAt = System.currentTimeMillis(),
            type = NotificationType.BUILD,
            message = "some-message"
        )
        notifications = notifications.plus(notification)
        assertEquals(1, notifications.valuesDesc.size)
        assertNotNull(notifications[notification.id])
        assertTrue { notifications.valuesDesc.contains(notification) }
    }

    @Test
    fun `replace - not existing notification`() {
        val notifications = Notifications()
        val id = "not-existing"
        val notExisting = Notification(
            id = id,
            agentId = agentId,
            createdAt = System.currentTimeMillis(),
            type = NotificationType.BUILD,
            message = "some-message"
        )
        assertEquals(notifications, notifications.replace(notExisting))
        assertNull(notifications[id])
    }

    @Test
    fun `replace - asc and desc must contain the same elements`() {
        var notifications = Notifications()
        val notification = Notification(
            id = "some-id",
            agentId = agentId,
            createdAt = System.currentTimeMillis(),
            type = NotificationType.BUILD,
            message = "some-message"
        )
        val replacement = notification.copy(read = true)
        notifications = notifications.plus(notification)
        notifications = notifications.replace(replacement)
        assertNotEquals(notification, notifications[replacement.id])
        assertTrue { notifications[replacement.id]!!.read }
        assertNotNull(notifications[replacement.id])
        assertTrue { notifications.valuesDesc.contains(replacement) }
    }

    @Test
    fun `minus - not existing notification`() {
        val notifications = Notifications()
        assertEquals(notifications, notifications.minus("not-existing"))
    }

    @Test
    fun `minus - asc and desc must contain the same elements`() {
        var notifications = Notifications()
        val notification = Notification(
            id = "some-id",
            agentId = agentId,
            createdAt = System.currentTimeMillis(),
            type = NotificationType.BUILD,
            message = "some-message"
        )
        notifications = notifications.plus(notification)
        notifications = notifications.minus(notification.id)
        assertNull(notifications[notification.id])
        assertFalse { notifications.valuesDesc.contains(notification) }
    }
}
