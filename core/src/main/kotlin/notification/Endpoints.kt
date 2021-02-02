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
package com.epam.drill.admin.notification

import com.epam.drill.admin.endpoints.*
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

    private val notificationManager by instance<NotificationManager>()
    private val topicResolver by instance<TopicResolver>()
    private val app by instance<Application>()

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
            topicResolver.sendToAllSubscribed(WsNotifications)
            call.respond(HttpStatusCode.OK, EmptyContent)
        }

        val deleteMeta = "".responds(ok<Unit>(), notFound())
        delete<ApiNotifications.Notification>(deleteMeta) { payload ->
            val notificationId = payload.id
            logger.info { "Delete notification $notificationId" }
            notificationManager.delete(notificationId)
            topicResolver.sendToAllSubscribed(WsNotifications)
            call.respond(HttpStatusCode.OK, EmptyContent)
        }

        val readAllMeta = "".responds(ok<Unit>(), notFound())
        patch<ApiNotifications.Read>(readAllMeta) {
            logger.info { "Read all notifications" }
            notificationManager.readAll()
            topicResolver.sendToAllSubscribed(WsNotifications)
            call.respond(HttpStatusCode.OK, EmptyContent)
        }

        val deleteAllMeta = "".responds(ok<Unit>(), notFound())
        delete<ApiNotifications>(deleteAllMeta) {
            logger.info { "Delete all notification" }
            notificationManager.deleteAll()
            topicResolver.sendToAllSubscribed(WsNotifications)
            call.respond(HttpStatusCode.OK, EmptyContent)
        }
    }
}
