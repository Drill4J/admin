package com.epam.drill.admin.notifications

import com.epam.drill.admin.notification.*
import org.kodein.di.*
import kotlin.test.*

class NotificationManagerTest {
    private val notificationManager = NotificationManager(Kodein.invoke { })
    private lateinit var notification: Notification

    @BeforeTest
    fun `save notification`() {
        notification = Notification(
            "some-id",
            "test-agent",
            System.currentTimeMillis(),
            NotificationType.BUILD,
            false,
            "some message"
        )
        notificationManager.save(notification)
    }

    @Test
    fun `save - notification must be added`() {
        val id = notification.id
        assertEquals(1, notificationManager.notifications.valuesDesc.size)
        assertNotNull(notificationManager.notifications[id])
        assertEquals(notification, notificationManager.notifications[id])
    }

    @Test
    fun `read - not existing notification`() {
        val id = "not-existing"
        assertFalse { notificationManager.read(id) }
        assertNull(notificationManager.notifications[id])
    }

    @Test
    fun `read - status must be changed`() {
        val id = notification.id
        assertTrue { notificationManager.read(id) }
        assertNotNull(notificationManager.notifications[id])
        assertTrue { notificationManager.notifications[id]!!.read }
    }
}
