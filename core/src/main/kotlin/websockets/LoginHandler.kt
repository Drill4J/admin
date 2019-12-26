package com.epam.drill.admin.websockets

import com.epam.drill.admin.*
import com.epam.drill.admin.common.*
import com.epam.drill.common.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.jwt.storage.*
import com.epam.drill.admin.jwt.validation.*
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
    val app: Application by instance()
    private val tokenManager: TokenManager by instance()
    private val logger = KotlinLogging.logger {}
    private val validator = RefreshTokenValidator(tokenManager)

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
                try {
                    val user = userSource.findUserByCredentials(credentials)
                    val token = JwtAuth.makeToken(user.id, user.role, TokenType.Access)
                    val refreshToken = JwtAuth.makeToken(user.id, user.role, TokenType.Refresh)
                    tokenManager.addToken(refreshToken)
                    call.response.apply {
                        header(HttpHeaders.Authorization, token)
                        header("Refresh", refreshToken)
                    }
                    logger.debug { "Login user with name $username was successfully" }
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }

            val refreshRequest = "Refresh access token"
                .examples(
                    example("token", "refresh token")
                )
                .responds(
                    ok<String>(
                        example("refresh token is valid", "new access token")
                    )
                )
            post<Routes.Api.RefreshToken, String>(refreshRequest) { _, refreshToken ->
                when (validator.validate(refreshToken)) {
                    ValidationResult.OK -> {
                        val accessToken = JwtAuth.refreshToken(refreshToken)
                        call.response.apply {
                            header(HttpHeaders.Authorization, accessToken)
                            header("Refresh", refreshToken)
                        }
                        logger.debug { "Token was successfully refreshed" }
                        call.respond(HttpStatusCode.OK)
                    }
                    ValidationResult.NO_SUCH_TOKEN ->
                        call.respond(HttpStatusCode.Unauthorized)
                    else -> {
                        tokenManager.deleteToken(refreshToken)
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
            }

            static {
                resources("public")
            }
        }
    }
}
