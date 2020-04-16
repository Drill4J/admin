package com.epam.drill.admin.endpoints

import com.epam.drill.admin.common.*
import com.epam.drill.common.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.server.testing.*
import kotlin.test.*

//TODO move under com.epam.drill.e2e

fun TestApplicationEngine.requestToken(): String {
    val token = handleRequest(HttpMethod.Post, "/api/login").run { response.headers[HttpHeaders.Authorization] }
    assertNotNull(token, "token can't be empty")
    return token
}

fun uiMessage(message: WsReceiveMessage) = (WsReceiveMessage.serializer() stringify message).toTextFrame()

fun agentMessage(type: MessageType, destination: String, message: String) =
    (Message.serializer() stringify Message(type, destination, message)).toTextFrame()

fun String.toTextFrame() = Frame.Text(this)
