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
package com.epam.drill.admin.common.route

import com.epam.drill.admin.common.model.DataResponse
import com.epam.drill.admin.common.model.MessageResponse
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.response.*

suspend inline fun <reified T> ApplicationCall.ok(data: T, message: String? = null) {
    respond(HttpStatusCode.OK, DataResponse(data, message))
}

suspend fun ApplicationCall.ok(message: String) {
    respond(HttpStatusCode.OK, MessageResponse(message))
}

suspend fun ApplicationCall.validationError(cause: Exception, defaultMessage: String = "Validation error") {
    respond(HttpStatusCode.BadRequest, MessageResponse(cause.message ?: defaultMessage))
}

suspend fun ApplicationCall.unauthorizedError(cause: Exception? = null) {
    respond(HttpStatusCode.Unauthorized, MessageResponse(cause?.message ?: "User is not authenticated"))
}

suspend fun ApplicationCall.accessDeniedError(cause: Exception? = null) {
    respond(HttpStatusCode.Forbidden, MessageResponse(cause?.message ?: "Access denied"))
}

suspend fun ApplicationCall.unprocessableEntity(cause: Exception? = null, defaultMessage: String = "Unprocessable entity") {
    respond(HttpStatusCode.UnprocessableEntity, MessageResponse(cause?.message ?: defaultMessage))
}

suspend fun ApplicationCall.notFound(cause: Exception) {
    respond(HttpStatusCode.NotFound, MessageResponse(cause.message ?: "Entity not found"))
}