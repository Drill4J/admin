package com.epam.drill.core

import com.auth0.jwt.exceptions.*
import com.epam.drill.common.*
import com.epam.drill.jwt.config.*
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
        logger.warn { "Authentication token is empty" }
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
        when (ex) {
            is TokenExpiredException -> Unit //Ignore, since we don't have token refreshing 
            else -> logger.debug { "Token '$token' verified was finished with exception" }
        }
        try {
            send(Frame.Text(WsSendMessage.serializer() stringify WsSendMessage(WsMessageType.UNAUTHORIZED)))
        } catch (ignored: ClosedSendChannelException) {
        }
        close()
    }
}