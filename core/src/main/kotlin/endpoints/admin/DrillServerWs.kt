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
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.admin

import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.core.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.websocket.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.channels.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*


class DrillServerWs(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val wsTopic by instance<WsTopic>()

    private val sessionStorage by instance<SessionStorage>()

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

                        val destination = event.destination
                        when (event) {
                            is Subscribe -> {
                                sessionStorage.subscribe(destination, session)
                                val message = wsTopic.resolve(destination)
                                val messageToSend = message.toWsMessageAsString(destination, WsMessageType.MESSAGE)
                                session.send(messageToSend)
                                logger.debug { "$destination is subscribed" }
                            }

                            is Unsubscribe -> {
                                if (sessionStorage.unsubscribe(destination, session)) {
                                    logger.debug { "$destination is unsubscribed" }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    when (e) {
                        is CancellationException -> logger.debug {
                            "$socketName: ${session.toDebugString()} was cancelled."
                        }
                        else -> logger.error(e) {
                            "$socketName: ${session.toDebugString()} finished with exception."
                        }
                    }
                } finally {
                    sessionStorage.release(session)
                    logger.debug { "$socketName: released ${session.toDebugString()}" }
                }
            }
        }
    }
}
