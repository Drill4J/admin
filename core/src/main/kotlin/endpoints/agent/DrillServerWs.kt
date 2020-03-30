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

    private val sessionStorage: SessionStorage by instance()

    init {
        app.routing {
            val socketName = "drill-admin-socket"
            authWebSocket("/ws/$socketName") {
                val session = this
                logger.debug { "$socketName: acquired ${session.toDebugString()}" }
                try {
                    incoming.consumeEach { frame ->
                        val json = (frame as Frame.Text).readText()
                        val event = WsReceiveMessage.serializer() parse json
                        logger.debug { "Receiving event $event" }

                        when (event) {
                            is Subscribe -> {
                                sessionStorage.subscribe(event.destination, session)
                                topicResolver.sendToAllSubscribed(event.destination)
                                logger.debug { "${event.destination} is subscribed" }
                            }

                            is Unsubscribe -> {
                                if (sessionStorage.unsubscribe(event.destination, session)) {
                                    logger.debug { "${event.destination} is unsubscribed" }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Finished with exception and session was removed" }

                } finally {
                    sessionStorage.release(session)
                    logger.debug { "$socketName: released ${session.toDebugString()}" }
                }
            }
        }
    }
}
