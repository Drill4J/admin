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
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

class DrillPluginWs(override val kodein: Kodein) : KodeinAware, Sender {

    private val app: Application by instance()
    private val cacheService: CacheService by instance()
    private val eventStorage: Cache<EventId, String> by cacheService

    private val sessionStorage get() = _sessionStorage.value
    private val _sessionStorage = atomic(
        persistentHashMapOf<String, PersistentSet<SessionData>>()
    )

    override suspend fun send(agentId: String, buildVersion: String, destination: Any, message: Any) {
        val dest = destination.toDestination(app)
        val eventId = EventId(
            agentId = agentId,
            destination = dest,
            buildVersion = buildVersion
        )

        //TODO replace with normal event removal
        if (message == "") {
            logger.info { "Removed message by id $eventId" }
            eventStorage.remove(eventId)
            return
        }

        val webSocketMessage = toWebSocketMessage(message, dest)
        val subscriber = AgentSubscriber(id = agentId, buildVersion = buildVersion)
        sendSubscribes(eventId, webSocketMessage, subscriber)
    }

    private fun toWebSocketMessage(message: Any, destination: String): String = if (message is List<*>) {
        @Suppress("UNCHECKED_CAST")
        val listMessage = message as List<Any>
        WsSendMessageListData.serializer() stringify WsSendMessageListData(
            WsMessageType.MESSAGE,
            destination,
            listMessage
        )
    } else {
        WsSendMessage.serializer() stringify WsSendMessage(
            WsMessageType.MESSAGE,
            destination,
            message
        )
    }

    private suspend fun sendSubscribes(eventId: EventId, webSocketMessage: String, currentSubscriber: Subscriber) {
        logger.debug { "Send data to destination=${eventId.destination}, message=$webSocketMessage" }
        eventStorage[eventId] = webSocketMessage
        sessionStorage[eventId.destination]?.let { sessionDataSet ->
            sessionDataSet.forEach { data ->
                try {
                    if (data.subscriber == currentSubscriber) {
                        data.session.send(Frame.Text(webSocketMessage))
                    }
                } catch (ex: Exception) {
                    when (ex) {
                        is ClosedSendChannelException,
                        is CancellationException -> logger.debug { "Channel for websocket ${eventId.destination} closed" }
                        else -> logger.error(ex) { "Sending data to ${eventId.destination} destination was finished with exception" }
                    }
                    data.session.removeSession(eventId.destination)
                }
            }
        } ?: logger.warn { "WS topic '${eventId.destination}' not registered yet" }
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
                                    val subscriber = jsonSubscribers.parse(Subscriber.serializer(), event.message)

                                    saveSession(event.destination, subscriber)

                                    val eventId = when (subscriber) {
                                        is AgentSubscriber -> {
                                            EventId(
                                                agentId = subscriber.id,
                                                destination = event.destination,
                                                buildVersion = subscriber.buildVersion
                                            )
                                        }
                                        else -> EventId()
                                    }
                                    val message = eventStorage[eventId]
                                    if (message.isNullOrEmpty()) {
                                        val emptyWsMessage = (WsSendMessage.serializer() stringify
                                                WsSendMessage(
                                                    WsMessageType.MESSAGE,
                                                    event.destination,
                                                    ""
                                                )).textFrame()
                                        this.send(emptyWsMessage)
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
        subscriber: Subscriber
    ) = _sessionStorage.update {
        val sessionData = SessionData(this, subscriber)
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

data class EventId(
    val agentId: String = "",
    val destination: String = "",
    val buildVersion: String = ""
) {
    override fun toString(): String = "$agentId:$destination:$buildVersion"
}

@Polymorphic
@Serializable
abstract class Subscriber {
    abstract val id: String
}

@Serializable
@SerialName("agent")
data class AgentSubscriber(
    override val id: String,
    val buildVersion: String
) : Subscriber()

@Serializable
@SerialName("group")
data class GroupSubscriber(
    override val id: String
) : Subscriber()

data class SessionData(
    val session: DefaultWebSocketServerSession,
    val subscriber: Subscriber
) {
    override fun equals(other: Any?) = other is SessionData && other.session == session
    override fun hashCode() = session.hashCode()
}

private val serializersModule = SerializersModule {
    polymorphic(Subscriber::class) {
        AgentSubscriber::class with AgentSubscriber.serializer()
        GroupSubscriber::class with GroupSubscriber.serializer()
    }
}

val jsonSubscribers = Json(context = serializersModule)

private fun Any.toDestination(app: Application): String =
    this as? String ?: app.toLocation(this)
