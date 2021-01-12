package com.epam.drill.admin.endpoints

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import kotlinx.serialization.json.*

fun Application.toLocation(rout: Any): String = locations().href(rout)

fun Application.locations() = featureOrNull(Locations) ?: Locations(this)

fun WebSocketSession.toDebugString(): String = "session(${hashCode()})"

fun Any.toWsMessageAsString(
    destination: String,
    type: WsMessageType,
    to: Subscription? = null
): String = when (this) {
    is Iterable<*> -> {
        @Suppress("UNCHECKED_CAST")
        val iterable = this as Iterable<Any>
        val list = (iterable as? List<Any>) ?: iterable.toList()
        WsSendMessageListData.serializer() stringify WsSendMessageListData(
            type = type,
            destination = destination,
            to = to.toJson(),
            message = list.toJsonList()
        )
    }
    else -> WsSendMessage.serializer() stringify WsSendMessage(
        type = type,
        destination = destination,
        to = to.toJson(),
        message = toJson()
    )
}

private fun Subscription?.toJson(): JsonElement = this?.let {
    Subscription.serializer() toJson it
} ?: JsonNull
