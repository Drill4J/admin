package com.epam.drill.admin.common

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
sealed class WsReceiveMessage {
    abstract val destination: String
    abstract val message: String
}

@Serializable
@SerialName("SUBSCRIBE")
data class Subscribe(
    override val destination: String,
    override val message: String = ""
) : WsReceiveMessage()

@Serializable
@SerialName("UNSUBSCRIBE")
data class Unsubscribe(
    override val destination: String,
    override val message: String = ""
) : WsReceiveMessage()

@Serializable
data class WsSendMessage(
    val type: WsMessageType,
    val destination: String = "",
    val to: JsonElement = JsonNull,
    val message: JsonElement = JsonPrimitive("")
)

@Serializable
data class WsSendMessageListData(
    val type: WsMessageType,
    val destination: String = "",
    val to: JsonElement = JsonNull,
    val message: List<JsonElement>
)

enum class WsMessageType {
    MESSAGE, DELETE, UNAUTHORIZED, SUBSCRIBE, UNSUBSCRIBE
}
