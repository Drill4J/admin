package com.epam.drill.admin.endpoints

import com.epam.drill.admin.common.*
import com.epam.drill.common.*
import com.epam.drill.admin.endpoints.agent.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.*
import mu.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

fun Application.toLocation(rout: Any): String {
    return this.locations.href(rout)
}

suspend fun SessionStorage.sendTo(
    destination: String,
    message: Any,
    type: WsMessageType = WsMessageType.MESSAGE
) {
    val iter = this.iterator()
    while (iter.hasNext()) {
        try {
            val it = iter.next()
            if (it.url == destination) {
                @Suppress("UNCHECKED_CAST") val frame =
                    if (message is Collection<*>) {
                        Frame.Text(
                            WsSendMessageListData.serializer() stringify WsSendMessageListData(
                                type,
                                destination,
                                (message as Collection<Any>).toMutableList()
                            )
                        )
                    } else {
                        Frame.Text(
                            WsSendMessage.serializer() stringify WsSendMessage(
                                type,
                                destination,
                                message
                            )
                        )
                    }
                it.send(frame)
                logger.debug { "Sent $frame through admin socket" }
            }
        } catch (ex: Exception) {
            when (ex) {
                is ClosedSendChannelException, is CancellationException -> logger.warn { "The socket connection was aborted" }
                else -> logger.error(ex) { "Processing drill ws session was finished with exception" }
            }
            iter.remove()
        }
    }
}

fun SessionStorage.exists(destination: String) = this.firstOrNull { it.url == destination } != null

fun SessionStorage.removeTopic(destination: String): Boolean = this.removeIf { it.url == destination }


fun String.textFrame() = Frame.Text(this)

data class DrillWsSession(var url: String? = null, val sourceSession: DefaultWebSocketServerSession) :
    DefaultWebSocketServerSession by sourceSession
