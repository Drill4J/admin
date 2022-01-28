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
package com.epam.drill.admin.endpoints.system

import com.epam.drill.admin.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.jwt.config.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.*
import mu.*

class LoginEndpoint(val app: Application) {
    private val logger = KotlinLogging.logger {}

    init {
        app.routing {
            val meta = "Login as guest"
                .examples(
                    example("user", UserData("guest", ""))
                )
                .responds(
                    ok<Unit>()
                )
            post<ApiRoot.Login, String>(meta) { _, userDataJson ->
                var notEmptyUserDataJson = userDataJson
                if (userDataJson.isBlank()) {
                    notEmptyUserDataJson = UserData.serializer() stringify UserData(
                        "guest",
                        ""
                    )
                }
                val userData = UserData.serializer() parse notEmptyUserDataJson
                val (username, password) = userData

                val credentials = UserPasswordCredential(username, password)
                logger.debug { "Login user with name $username" }
                val user = userSource.findUserByCredentials(credentials)
                val token = JwtConfig.makeToken(user, app.jwtLifetime)
                call.response.header(HttpHeaders.Authorization, token)
                logger.debug { "Login user with name $username was successfully" }
                call.respond(HttpStatusCode.OK, JWT(token))
            }

            static {
                resources("public")
            }
        }
    }
}

@Serializable
private data class JWT(val token: String)
