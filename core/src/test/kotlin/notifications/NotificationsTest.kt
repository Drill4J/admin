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
        notifications += notification
        notifications += notification
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
        notifications += notification
        assertEquals(1, notifications.valuesDesc.size)
        assertNotNull(notifications[notification.id])
        assertTrue { notification in notifications.valuesDesc }
    }

    @Test
    fun `replace - not existing notification`() {
        val notifications = Notifications()
        val notExisting = Notification(
            id = "not-existing",
            agentId = agentId,
            createdAt = System.currentTimeMillis(),
            type = NotificationType.BUILD,
            message = "some-message"
        )
        assertEquals(notifications, notifications.replace(notExisting))
        assertNull(notifications[notExisting.id])
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
        notifications += notification
        notifications = notifications.replace(replacement)
        assertNotEquals(notification, notifications[replacement.id])
        assertTrue { notifications[replacement.id]!!.read }
        assertNotNull(notifications[replacement.id])
        assertTrue { replacement in notifications.valuesDesc }
    }

    @Test
    fun `minus - not existing notification`() {
        val notifications = Notifications()
        assertEquals(notifications, notifications - "not-existing")
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
        notifications += notification
        notifications -= notification.id
        assertNull(notifications[notification.id])
        assertFalse { notification in notifications.valuesDesc }
    }

    @Test
    fun `updateAll - all tests must be updated`() {
        var notifications = Notifications()
        val notification = Notification(
            id = "some-id",
            agentId = agentId,
            createdAt = System.currentTimeMillis(),
            type = NotificationType.BUILD,
            message = "some-message"
        )
        val secondNotification = Notification(
            id = "second-notification",
            agentId = agentId,
            createdAt = System.currentTimeMillis(),
            type = NotificationType.BUILD,
            message = "another-message"
        )
        notifications += notification
        notifications += secondNotification
        notifications = notifications.updateAll { it.copy(read = true) }
        assertEquals(2, notifications.valuesDesc.size)
        assertTrue { notifications[notification.id]!!.read }
        assertTrue { notifications[secondNotification.id]!!.read }
        assertTrue { notifications.valuesDesc.map { it.read }.all { it } }
    }
}
