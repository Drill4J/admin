package com.epam.drill.admin.endpoints

import com.epam.drill.admin.common.*
import com.epam.drill.common.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*

fun Application.toLocation(rout: Any): String {
    return this.locations.href(rout)
}

fun WebSocketSession.toDebugString(): String = "session(${hashCode()})"

fun Any.toWsMessageAsString(
    destination: String,
    type: WsMessageType
): String = when (this) {
    is Iterable<*> -> {
        @Suppress("UNCHECKED_CAST")
        val list = (this as Iterable<Any>).toList()
        WsSendMessageListData.serializer() stringify WsSendMessageListData(
            type,
            destination,
            list
        )
    }
    else -> WsSendMessage.serializer() stringify WsSendMessage(
        type,
        destination,
        this
    )
}
