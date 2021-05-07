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
    override val message: String = "",
) : WsReceiveMessage()

@Serializable
@SerialName("UNSUBSCRIBE")
data class Unsubscribe(
    override val destination: String,
    override val message: String = "",
) : WsReceiveMessage()

@Serializable
data class WsSendMessage(
    val type: WsMessageType,
    val destination: String = "",
    val to: JsonElement = JsonNull,
    val message: JsonElement = JsonPrimitive(""),
)

@Serializable
data class WsSendMessageListData(
    val type: WsMessageType,
    val destination: String = "",
    val to: JsonElement = JsonNull,
    val message: List<JsonElement>,
)

enum class WsMessageType {
    MESSAGE, DELETE, UNAUTHORIZED, SUBSCRIBE, UNSUBSCRIBE
}
