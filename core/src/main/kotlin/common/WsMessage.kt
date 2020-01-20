package com.epam.drill.admin.common

import kotlinx.serialization.*

@Serializable
data class WsReceiveMessage(
    val type: WsMessageType,
    val destination: String = "",
    val message: String = ""
)

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
    val message: List<@ContextualSerialization  Any>
)

enum class WsMessageType {
    MESSAGE, DELETE, UNAUTHORIZED, SUBSCRIBE, UNSUBSCRIBE
}
