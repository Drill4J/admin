package com.epam.drill

import com.epam.drill.jwt.config.*
import com.epam.drill.jwt.user.source.*
import com.epam.drill.kodein.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.websocket.*
import java.io.*
import java.time.*


val drillHomeDir = File(System.getenv("DRILL_HOME") ?: "")

val drillWorkDir = drillHomeDir.resolve("work")

val userSource: UserSource = UserSourceImpl()

@Suppress("unused")
fun Application.module() = kodeinApplication(
    AppBuilder {
        withInstallation {
            @Suppress("UNUSED_VARIABLE") val jwtAudience = environment.config.property("jwt.audience").getString()
            val jwtRealm = environment.config.property("jwt.realm").getString()

            install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
                    throw cause
                }
            }
            install(CallLogging)
            install(Locations)
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(150)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            install(Authentication) {
                jwt {
                    realm = jwtRealm
                    verifier(JwtConfig.verifier)
                    validate {
                        it.payload.getClaim("id").asInt()?.let(userSource::findUserById)
                    }
                }
            }

            install(CORS) {
                anyHost()
                allowCredentials = true
                method(HttpMethod.Post)
                method(HttpMethod.Get)
                method(HttpMethod.Delete)
                method(HttpMethod.Put)
                method(HttpMethod.Patch)
                header(HttpHeaders.Authorization)
                header(HttpHeaders.ContentType)
                exposeHeader(HttpHeaders.Authorization)
                exposeHeader(HttpHeaders.ContentType)
            }

        }
        withKModule { kodeinModule("storage", storage) }
        withKModule { kodeinModule("wsHandler", wsHandler) }
        withKModule { kodeinModule("handlers", handlers) }
        withKModule { kodeinModule("pluginServices", pluginServices) }
    }
)