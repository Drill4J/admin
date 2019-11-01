package com.epam.drill.endpoints

import com.epam.drill.common.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

fun TestApplicationEngine.requestToken(): String {
    val token = handleRequest(HttpMethod.Post, "/api/login").run { response.headers[HttpHeaders.Authorization] }
    assertNotNull(token, "token can't be empty")
    return token
}

fun UiMessage(type: WsMessageType, destination: String, message: String="") =
    (WsSendMessage.serializer() stringify WsSendMessage(type, destination, message)).textFrame()


fun AgentMessage(type: MessageType, destination: String, message: String) =
    (Message.serializer() stringify Message(type, destination, message)).textFrame()