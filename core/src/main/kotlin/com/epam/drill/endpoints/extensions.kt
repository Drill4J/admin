package com.epam.drill.endpoints

import com.epam.drill.common.*
import com.epam.drill.endpoints.agent.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.websocket.*

fun Application.toLocation(rout: Any): String {
    return this.locations.href(rout)
}

suspend fun MutableSet<DrillWsSession>.sendTo(
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
            }
        } catch (ex: Exception) {
            iter.remove()
        }
    }
}

fun SessionStorage.exists(destination: String) = this.firstOrNull { it.url == destination } != null

fun SessionStorage.removeTopic(destination: String) {
    if (this.removeIf { it.url == destination })
        println("$destination unsubscribe")
}

fun String.textFrame() = Frame.Text(this)

data class DrillWsSession(var url: String? = null, val sourceSession: DefaultWebSocketServerSession) :
    DefaultWebSocketServerSession by sourceSession

fun <E> MutableSet<E>.replaceAll(set: MutableSet<E>) {
    this.clear()
    this.addAll(set)
}