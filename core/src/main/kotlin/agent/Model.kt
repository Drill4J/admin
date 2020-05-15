package com.epam.drill.admin.agent

import com.epam.drill.common.*
import io.ktor.util.*
import kotlinx.serialization.*

sealed class AgentMessage {
    abstract val type: MessageType
    abstract val destination: String

    abstract val text: String
    abstract val bytes: ByteArray
}

@Serializable
data class JsonMessage(
    override val type: MessageType,
    override val destination: String = "",
    override val text: String = ""
) : AgentMessage() {
    override val bytes: ByteArray get() = text.decodeBase64Bytes()

    override fun toString() = "Json(type=$type,destination=$destination,text=$text)"
}

class BinaryMessage(
    val message: Message
) : AgentMessage() {
    override val type: MessageType get() = message.type
    override val destination: String get() = message.destination

    override val text get() = bytes.decodeToString()
    override val bytes get() = message.data

    override fun toString() = "Binary(type=$type,destination=$destination,size=${bytes.size})"
}
