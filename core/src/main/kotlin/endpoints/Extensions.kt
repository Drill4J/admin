/**
 * Copyright 2020 - 2022 EPAM Systems
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
    to: Subscription? = null,
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
