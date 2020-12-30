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
                call.respond(HttpStatusCode.OK)
            }

            static {
                resources("public")
            }
        }
    }
}
