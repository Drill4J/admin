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
package com.epam.drill.admin.core

import com.auth0.jwt.exceptions.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.common.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import mu.*

private val logger = KotlinLogging.logger {}

fun Route.authWebSocket(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit,
) {
    webSocket(path, protocol) {
        socketAuthentication()
        try {
            handler(this)
        } catch (ex: Exception) {
            closeExceptionally(ex)
        }
    }
}

private suspend fun DefaultWebSocketServerSession.socketAuthentication() {
    val token = call.parameters["token"]

    if (token == null) {
        logger.warn { "Authentication token is empty" }
        send(WsSendMessage.serializer() stringify WsSendMessage(WsMessageType.UNAUTHORIZED))
        close()
        return
    }
    verifyToken(token)

    launch {
        while (true) {
            delay(10_000)
            verifyToken(token)
        }
    }
}

private suspend fun DefaultWebSocketServerSession.verifyToken(token: String) {

    try {
        JwtConfig.verifier.verify(token)
    } catch (ex: JWTVerificationException) {
        when (ex) {
            is TokenExpiredException -> Unit //Ignore, since we don't have token refreshing 
            else -> logger.debug { "Token '$token' verified was finished with exception" }
        }
        try {
            send(
                Frame.Text(
                    WsSendMessage.serializer() stringify WsSendMessage(
                        WsMessageType.UNAUTHORIZED
                    )
                )
            )
        } catch (ignored: ClosedSendChannelException) {
        }
        close()
    }
}
