@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.agent

import com.epam.drill.admin.common.*
import com.epam.drill.admin.core.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.common.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import kotlinx.coroutines.channels.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*


class DrillServerWs(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val app: Application by instance()
    private val topicResolver: TopicResolver by instance()

    //FIXME Mutable set!
    private val sessionStorage: SessionStorage by instance()

    init {
        app.routing {
            authWebSocket("/ws/drill-admin-socket") {
                val rawWsSession = this
                logger.debug { "New session drill-admin-socket" }

                try {
                    incoming.consumeEach { frame ->
                        val json = (frame as Frame.Text).readText()
                        val event = WsReceiveMessage.serializer() parse json
                        logger.debug { "Receiving event $event" }

                        when (event) {
                            is Subscribe -> {
                                val wsSession = DrillWsSession(event.destination, rawWsSession)
                                subscribe(wsSession, event)
                                logger.debug { "${event.destination} is subscribed" }
                            }

                            is Unsubscribe -> {
                                if (sessionStorage.removeTopic(event.destination)) {
                                    logger.debug { "${event.destination} is unsubscribed" }
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Finished with exception and session was removed" }
                    sessionStorage.remove(rawWsSession)
                }
            }
        }
    }

    private suspend fun subscribe(wsSession: DrillWsSession, event: WsReceiveMessage) {
        sessionStorage += (wsSession)
        topicResolver.sendToAllSubscribed(event.destination)
    }
}
