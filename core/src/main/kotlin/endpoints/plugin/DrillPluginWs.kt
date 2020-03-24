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
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

class DrillPluginWs(override val kodein: Kodein) : KodeinAware, Sender {

    private val app: Application by instance()
    private val agentManager: AgentManager by instance()
    private val cacheService: CacheService by instance()
    private val eventStorage: Cache<Any, String> by cacheService

    private val sessionStorage get() = _sessionStorage.value
    private val _sessionStorage = atomic(
        persistentHashMapOf<String, PersistentMap<String, DefaultWebSocketSession>>()
    )

    override suspend fun send(agentId: String, buildVersion: String, destination: Any, message: Any) {
        val dest = destination as? String ?: app.toLocation(destination)
        val subscription = AgentSubscription(agentId, buildVersion)
        val id = subscription.toKey(dest)

        //TODO replace with normal event removal
        if (message == "") {
            logger.info { "Removed message by key $id" }
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
            sessionStorage[dest]?.let { it[subscription.toKey()] }?.let { session ->
                try {
                    session.send(Frame.Text(messageForSend))
                } catch (ex: Exception) {
                    when (ex) {
                        is ClosedSendChannelException,
                        is CancellationException -> logger.debug { "Channel for websocket $id closed" }
                        else -> logger.error(ex) { "Sending data to $id destination was finished with exception" }
                    }
                    session.remove(dest)
                }
            } ?: logger.warn { "WS topic '$dest' not registered yet" }
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
                            logger.debug { "Receiving event $event" }

                            when (event) {
                                is Subscribe -> {
                                    //TODO remove type field existence check after changes on front end
                                    val subscription: Subscription = (JsonObject.serializer() parse event.message)
                                        .let { json ->
                                            Subscription.serializer().takeIf {
                                                "type" in json
                                            } ?: AgentSubscription.serializer()
                                        }.parse(event.message).ensureBuildVersion()
                                    save(event.destination, subscription)

                                    val message: String? = eventStorage[subscription.toKey(event.destination)]
                                    val messageToSend = if (message.isNullOrEmpty()) {
                                        WsSendMessage.serializer().stringify(
                                            WsSendMessage(
                                                type = WsMessageType.MESSAGE,
                                                destination = event.destination
                                            )
                                        )
                                    } else message
                                    send(messageToSend.textFrame())
                                    logger.debug { "${event.destination} is subscribed" }
                                }
                                is Unsubscribe -> remove(event.destination)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Subscription.ensureBuildVersion() = if (this is AgentSubscription && buildVersion == null) {
        copy(buildVersion = agentManager.buildVersionByAgentId(agentId))
    } else this

    //TODO move session storage to a separate file
    private fun DefaultWebSocketSession.save(
        dest: String,
        subscription: Subscription
    ) = _sessionStorage.update {
        val key = subscription.toKey()
        val value = it[dest]?.put(key, this) ?: persistentHashMapOf(key to this)
        it.put(dest, value)
    }

    private fun DefaultWebSocketSession.remove(
        dest: String
    ) = _sessionStorage.update {
        it[dest]?.let { sessions ->
            //TODO this removal by session is strange - investigate whether it's necessary
            val keysToRemove = sessions.entries
                .filter { (_, v) -> v == this }
                .map { (k, _) -> k }
            (sessions - keysToRemove).takeIf { updated ->
                sessions.size != updated.size
            }
        }?.let { sessions ->
            logger.debug { "$dest is unsubscribed" }
            if (sessions.isNotEmpty()) {
                it.put(dest, sessions)
            } else it - dest
        } ?: it
    }
}

@Serializable
sealed class Subscription {
    abstract fun toKey(destination: String = ""): String
}

@Serializable
@SerialName("AGENT")
data class AgentSubscription(
    val agentId: String,
    val buildVersion: String? = null
) : Subscription() {
    override fun toKey(destination: String) = "agent::$agentId:$buildVersion/$destination"
}

@Serializable
@SerialName("GROUP")
data class GroupSubscription(
    val groupId: String
) : Subscription() {
    override fun toKey(destination: String) = "group::$groupId/$destination"
}
