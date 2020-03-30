package com.epam.drill.admin.notification

import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*


class NotificationEndpoints(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val notificationManager: NotificationManager by instance()
    private val app: Application by instance()

    init {
        app.routing {
            authenticate { authenticated() }
        }
    }

    private fun Route.authenticated() {
        val readMeta = "".responds(ok<Unit>(), notFound())
        patch<ApiNotifications.Notification.Read>(readMeta) { read ->
            val notificationId = read.parent.id
            logger.info { "Read notification $notificationId" }
            notificationManager.read(notificationId)
            call.respond(HttpStatusCode.OK, EmptyContent)
        }

        val deleteMeta = "".responds(ok<Unit>(), notFound())
        delete<ApiNotifications.Notification>(deleteMeta) { payload ->
            val notificationId = payload.id
            logger.info { "Delete notification $notificationId" }
            notificationManager.delete(notificationId)
            call.respond(HttpStatusCode.OK, EmptyContent)
        }
    }
}
