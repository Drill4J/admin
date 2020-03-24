package com.epam.drill.admin.common

import kotlinx.serialization.*

@Serializable
sealed class WsReceiveMessage {
    abstract val destination: String

}

@Serializable
@SerialName("SUBSCRIBE")
data class Subscribe(
    override val destination: String,
    val message: String = "" //TODO replace with Subscription
) : WsReceiveMessage()

@Serializable
@SerialName("UNSUBSCRIBE")
data class Unsubscribe(
    override val destination: String
) : WsReceiveMessage()


@Serializable
data class WsSendMessage(
    val type: WsMessageType,
    val destination: String = "",
    @ContextualSerialization val message: Any = ""
)

@Serializable
data class WsSendMessageListData(
    val type: WsMessageType,
    val destination: String = "",
    val message: List<@ContextualSerialization Any>
)

enum class WsMessageType {
    MESSAGE, DELETE, UNAUTHORIZED, SUBSCRIBE, UNSUBSCRIBE
}
