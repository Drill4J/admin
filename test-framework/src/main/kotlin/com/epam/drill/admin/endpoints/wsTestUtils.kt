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

import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.common.message.*
import com.epam.drill.e2e.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.server.testing.*
import kotlinx.serialization.protobuf.*
import kotlin.test.*

//TODO move under com.epam.drill.e2e

fun TestApplicationEngine.requestToken(): String {
    val loginUrl = toApiUri(ApiRoot().let { ApiRoot.Login(it) })
    val token = handleRequest(HttpMethod.Post, loginUrl) {
        addHeader(HttpHeaders.ContentType, "${ContentType.Application.Json}")
        setBody(UserData.serializer() stringify UserData("guest", ""))
    }.run { response.headers[HttpHeaders.Authorization] }
    assertNotNull(token, "token can't be empty")
    return token
}

fun uiMessage(message: WsReceiveMessage) = (WsReceiveMessage.serializer() stringify message).toTextFrame()

fun agentMessage(type: MessageType, destination: String, message: ByteArray = byteArrayOf()) =
    ProtoBuf.dump(Message.serializer(), Message(type, destination, message)).toByteFrame()

fun String.toTextFrame() = Frame.Text(this)

fun ByteArray.toByteFrame() = Frame.Binary(true, this)
