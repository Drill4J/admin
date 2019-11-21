package com.epam.drill.notifications

import com.epam.drill.common.*
import com.epam.drill.dataclasses.*
import com.epam.drill.util.*
import java.util.*
import kotlin.test.*

const val AGENT_ID = "testId"

class NotificationManagerTest {

    private val manager = NotificationsManager()

    @Test
    fun `saving process is correct`() {
        saveRandomBuildNotification(3)
        val notification = manager.allNotifications.first()
        assertEquals(AGENT_ID, notification.agentId)
        assertEquals(3, manager.allNotifications.count())
        manager.deleteAll()
    }

    @Test
    fun `reading process is correct`() {
        saveRandomBuildNotification(3)
        val notificationId = manager.allNotifications.first().id
        manager.read(notificationId)
        val notification = manager.allNotifications.find { it.id == notificationId }
        assertEquals(NotificationStatus.READ, notification!!.status)
        manager.readAll()
        val unreadCount = manager.allNotifications.count { it.status == NotificationStatus.UNREAD }
        assertEquals(0, unreadCount)
        manager.deleteAll()
    }

    @Test
    fun `deleting process is correct`() {
        saveRandomBuildNotification(3)
        val notificationId = manager.allNotifications.first().id
        manager.delete(notificationId)
        val notification = manager.allNotifications.find { it.id == notificationId }
        assertNull(notification)
        manager.deleteAll()
        assertEquals(0, manager.allNotifications.count())
    }

    private fun saveRandomBuildNotification(count: Int) {
        var previousVersion = ""

        for (i in 1..count) {
            val buildVersion = UUID.randomUUID().toString()

            manager.save(
                AGENT_ID,
                "testName",
                NotificationType.BUILD,
                NewBuildArrivedMessage.serializer() stringify
                        NewBuildArrivedMessage(
                            buildVersion,
                            previousVersion,
                            "prevAlias",
                            BuildDiff(1, 2, 3, 4, 5),
                            listOf("recommendation_1", "recommendation_2")
                        )
            )
            previousVersion = buildVersion
        }
    }
}

