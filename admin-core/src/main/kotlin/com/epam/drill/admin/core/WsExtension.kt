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
package com.epam.drill.admin.core

import com.auth0.jwt.exceptions.*
import com.epam.drill.admin.auth.config.withRole
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.auth.service.TokenService
import io.ktor.auth.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import mu.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI as di

private val logger = KotlinLogging.logger {}

fun Route.authWebSocket(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit,
) {
    val tokenService by di().instance<TokenService>()

    authenticate("jwt") {
        withRole(Role.USER) {
            webSocket(path, protocol) {
                socketAuthentication(tokenService)
                try {
                    handler(this)
                } catch (ex: Exception) {
                    closeExceptionally(ex)
                }
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.socketAuthentication(tokenService: TokenService) {
    val token = call.request.cookies["jwt"]

    if (token.isNullOrEmpty()) {
        logger.warn { "Authentication token is empty" }
        send(WsSendMessage.serializer() stringify WsSendMessage(WsMessageType.UNAUTHORIZED))
        close()
        return
    }

    launch {
        while (true) {
            delay(10_000)
            verifyToken(token, tokenService)
        }
    }
}

private suspend fun DefaultWebSocketServerSession.verifyToken(token: String, tokenService: TokenService) {

    try {
        tokenService.verifyToken(token)
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
