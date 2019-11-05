package com.epam.drill.core

import com.auth0.jwt.exceptions.*
import com.epam.drill.common.*
import com.epam.drill.jwt.config.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import mu.*

val logger = KotlinLogging.logger {}

fun Route.authWebSocket(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    webSocket(path, protocol) {
        socketAuthentication()
        handler(this)
    }
}

private suspend fun DefaultWebSocketServerSession.socketAuthentication() {
    val token = call.parameters["token"]
    if (token == null) {
        logger.error { "Authentication token is empty" }
        send(Frame.Text(WsSendMessage.serializer() stringify WsSendMessage(WsMessageType.UNAUTHORIZED)))
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
        logger.error { "Token '$token' verified with exception '${ex.message}'" }
        send(Frame.Text(WsSendMessage.serializer() stringify WsSendMessage(WsMessageType.UNAUTHORIZED)))
        close()
    }
}