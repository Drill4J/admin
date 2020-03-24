package com.epam.drill.admin.notification

import de.nielsfalk.ktor.swagger.version.shared.*
import io.ktor.locations.*

@Location("/api/notifications")
object ApiNotifications {
    @Location("/{id}")
    data class Notification(val id: String) {
        @Group("Notification Endpoints")
        @Location("/read")
        data class Read(val parent: Notification)
    }
}

@Location("/notifications")
object WsNotifications
