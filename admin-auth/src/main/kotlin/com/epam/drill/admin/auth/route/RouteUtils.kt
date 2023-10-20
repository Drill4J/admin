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
package com.epam.drill.admin.auth.route

import com.epam.drill.admin.auth.model.DataResponse
import com.epam.drill.admin.auth.model.MessageResponse
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*

suspend inline fun <reified T> ApplicationCall.ok(data: T, message: String? = null) {
    respond(HttpStatusCode.OK, DataResponse(data, message))
}

suspend fun ApplicationCall.ok(message: String) {
    respond(HttpStatusCode.OK, MessageResponse(message))
}