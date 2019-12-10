package com.epam.drill.endpoints

import com.epam.drill.common.*
import com.epam.drill.service.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*

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