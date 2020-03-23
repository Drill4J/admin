@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.core.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

class DrillPluginWs(override val kodein: Kodein) : KodeinAware, Sender {

    private val app: Application by instance()
    private val agentManager: AgentManager by instance()
    private val cacheService: CacheService by instance()
    private val eventStorage: Cache<String, String> by cacheService

    private val sessionStorage get() = _sessionStorage.value
    private val _sessionStorage = atomic(
        persistentHashMapOf<String, PersistentSet<SessionData>>()
    )

    override suspend fun send(agentId: String, buildVersion: String, destination: Any, message: Any) {
        val dest = destination as? String ?: app.toLocation(destination)
        val id = "$agentId:$dest:$buildVersion"

        //TODO replace with normal event removal
        if (message == "") {
            logger.info { "Removed message by id $id" }
            eventStorage.remove(id)
        } else {
            val messageForSend = if (message is List<*>) {
                @Suppress("UNCHECKED_CAST")
                val listMessage = message as List<Any>
                WsSendMessageListData.serializer() stringify WsSendMessageListData(
                    WsMessageType.MESSAGE,
                    dest,
                    listMessage
                )
            } else {
                WsSendMessage.serializer() stringify WsSendMessage(
                    WsMessageType.MESSAGE,
                    dest,
                    message
                )
            }
            logger.debug { "Send data to $id destination" }
            eventStorage[id] = messageForSend
            sessionStorage[dest]?.let { sessionDataSet ->
                sessionDataSet.forEach { data ->
                    try {

                        if (data.subscribeInfo == SubscribeInfo(agentId, buildVersion)) {
                            data.session.send(Frame.Text(messageForSend))
                        }
                    } catch (ex: Exception) {
                        when (ex) {
                            is ClosedSendChannelException,
                            is CancellationException -> logger.debug { "Channel for websocket $id closed" }
                            else -> logger.error(ex) { "Sending data to $id destination was finished with exception" }
                        }
                        data.session.removeSession(dest)
                    }
                }
            } ?: run {
                logger.warn { "WS topic '$dest' not registered yet" }
            }
        }
    }

    init {
        app.routing {
            authWebSocket("/ws/drill-plugin-socket") {
                logger.debug { "New session drill-plugin-socket" }

                incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            val event = WsReceiveMessage.serializer() parse frame.readText()
                            logger.debug { "Receive event with: destination '${event.destination}', type '${event.type}' and message '${event.message}'" }

                            when (event.type) {
                                WsMessageType.SUBSCRIBE -> {
                                    val subscribeInfo = SubscribeInfo.serializer() parse event.message

                                    saveSession(event.destination, subscribeInfo)
                                    val buildVersion = subscribeInfo.buildVersion

                                    val message =
                                        eventStorage[
                                            subscribeInfo.agentId + ":" +
                                                event.destination + ":" +
                                                if (buildVersion.isNullOrEmpty()) {
                                                    agentManager.buildVersionByAgentId(subscribeInfo.agentId)
                                                } else buildVersion
                                        ]

                                    if (message.isNullOrEmpty()) {
                                        this.send(
                                            (WsSendMessage.serializer() stringify
                                                WsSendMessage(
                                                    WsMessageType.MESSAGE,
                                                    event.destination,
                                                    ""
                                                )).textFrame()
                                        )
                                    } else {
                                        this.send(Frame.Text(message))
                                    }
                                    logger.debug { "${event.destination} is subscribed" }
                                }

                                WsMessageType.UNSUBSCRIBE -> removeSession(event.destination)
                                else -> {
                                    logger.warn { "Event '${event.type}' is not implemented yet" }
                                    close()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    //TODO move session storage to a separate file
    private fun DefaultWebSocketServerSession.saveSession(
        destination: String,
        subscribeInfo: SubscribeInfo
    ) = _sessionStorage.update {
        val sessionData = SessionData(this, subscribeInfo)
        val sessions = it[destination]?.run {
            add(sessionData)
        } ?: persistentSetOf(sessionData)
        it.put(destination, sessions)
    }

    private fun DefaultWebSocketServerSession.removeSession(
        destination: String
    ) = _sessionStorage.update {
        it[destination]?.let { sessions ->
            sessions.removeAll { data ->
                data.session == this
            }.takeIf { updated -> updated.size != sessions.size }
        }?.let { sessions ->
            logger.debug { "$destination is unsubscribed" }
            it.put(destination, sessions)
        } ?: it
    }
}

@Serializable
data class SubscribeInfo(
    val agentId: String,
    val buildVersion: String? = null
)

data class SessionData(
    val session: DefaultWebSocketServerSession,
    val subscribeInfo: SubscribeInfo
) {
    override fun equals(other: Any?) = other is SessionData && other.session == session
    override fun hashCode() = session.hashCode()
}
