package com.epam.drill.admin.websockets

import com.epam.drill.admin.*
import com.epam.drill.admin.common.*
import com.epam.drill.common.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.router.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

class LoginHandler(override val kodein: Kodein) : KodeinAware {

    val app by instance<Application>()

    private val logger = KotlinLogging.logger {}

    init {
        app.routing {
            val loginResponds = "Login as guest"
                .examples(
                    example("user", UserData("guest", ""))
                )
                .responds(
                    ok<String>(

                    )
                )
            post<Routes.Api.Login, String>(loginResponds) { _, userDataJson ->
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
