/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.agent

import io.ktor.util.*
import kotlinx.serialization.*

typealias MessageType = com.epam.drill.common.MessageType
typealias Message = com.epam.drill.common.Message

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
    override val text: String = "",
) : AgentMessage() {
    override val bytes: ByteArray get() = text.decodeBase64Bytes()

    override fun toString() = "Json(type=$type,destination=$destination,text=$text)"
}

class BinaryMessage(
    val message: Message,
) : AgentMessage() {
    override val type: MessageType get() = message.type
    override val destination: String get() = message.destination

    override val text get() = bytes.decodeToString()
    override val bytes get() = message.data

    override fun toString() = "Binary(type=$type,destination=$destination,size=${bytes.size})"
}
