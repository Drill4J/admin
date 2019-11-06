package com.epam.drill.websockets

import com.epam.drill.*
import com.epam.drill.jwt.config.*
import com.epam.drill.router.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

class LoginHandler(override val kodein: Kodein) : KodeinAware {
    val app: Application by instance()
    private val logger = KotlinLogging.logger {}

    init {
        app.routing {
            post<Routes.Api.Login> {
                val username = "guest"
                val password = ""
                logger.debug { "Login user with name $username" }
                val credentials = UserPasswordCredential(username, password)
                val user = userSource.findUserByCredentials(credentials)
                val token = JwtConfig.makeToken(user)
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