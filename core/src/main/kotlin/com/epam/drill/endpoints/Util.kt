package com.epam.drill.endpoints

import com.epam.drill.common.*
import com.epam.drill.service.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import kotlinx.serialization.*

fun<T> KSerializer<T>.agentWsMessage(destination: String, message: T): Frame.Text {
    val toJson = Message.serializer() stringify
        Message(MessageType.MESSAGE, destination, if (message is String) message else this stringify message)

    println(toJson)
    return Frame.Text(toJson)
}

fun Parameters.asMap() = names().map { name ->
    name to (get(name) ?: "")
}.toMap()

suspend fun ApplicationCall.respondJsonIfErrorsOccured(statusCode: HttpStatusCode, message: String) =
    if (statusCode == HttpStatusCode.OK) {
        respond(statusCode, message)
    } else {
        respondText(
            ValidationResponse.serializer() stringify ValidationResponse(message),
            ContentType.Application.Json,
            statusCode
        )
    }