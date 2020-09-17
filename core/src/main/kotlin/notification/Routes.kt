package com.epam.drill.admin.notification

import de.nielsfalk.ktor.swagger.version.shared.*
import io.ktor.locations.*

@Group("Notification Endpoints")
@Location("/api/notifications")
object ApiNotifications {
    @Group("Notification Endpoints")
    @Location("/{id}")
    data class Notification(val id: String) {
        @Group("Notification Endpoints")
        @Location("/read")
        data class Read(val parent: Notification)
    }

    @Group("Notification Endpoints")
    @Location("/read")
    data class Read(val parent: ApiNotifications)
}

@Location("/notifications")
object WsNotifications
