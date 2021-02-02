/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.notifications

import com.epam.drill.admin.notification.*
import kotlinx.serialization.json.*
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
            JsonPrimitive("some message")
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

    @Test
    fun `readAll - statuses must be changed`() {
        val id = notification.id
        val notification = notification.copy(id = "second-notification")
        notificationManager.save(notification)
        assertEquals(2, notificationManager.readAll().valuesDesc.size)
        assertTrue { notificationManager.notifications[id]!!.read }
        assertTrue { notificationManager.notifications[notification.id]!!.read }
    }

    @Test
    fun `delete - not existing notification`() {
        val id = "not-existing"
        assertFalse { notificationManager.delete(id) }
        assertNull(notificationManager.notifications[id])
    }

    @Test
    fun `delete - notification must be deleted`() {
        val id = notification.id
        assertNotNull(notificationManager.notifications[id])
        assertTrue { notificationManager.delete(id) }
        assertNull(notificationManager.notifications[id])
    }

    @Test
    fun `deleteAll - notifications must be deleted`() {
        assertEquals(1, notificationManager.notifications.valuesDesc.size)
        val notifications = notificationManager.deleteAll()
        assertEquals(1, notifications.valuesDesc.size)
        assertEquals(0, notificationManager.notifications.valuesDesc.size)
    }
}
