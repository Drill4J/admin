package com.epam.drill

import com.epam.drill.jwt.config.*
import com.epam.drill.jwt.user.source.*
import com.epam.drill.kodein.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.serialization.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*
import mu.*
import java.io.*
import java.time.*


val drillHomeDir = File(System.getenv("DRILL_HOME") ?: "")

val drillWorkDir = drillHomeDir.resolve("work")

val userSource: UserSource = UserSourceImpl()

private val logger = KotlinLogging.logger {}

@Suppress("unused")
fun Application.module() = kodeinApplication(
    AppBuilder {
        withInstallation {
            @Suppress("UNUSED_VARIABLE") val jwtAudience = environment.config.property("jwt.audience").getString()
            val jwtRealm = environment.config.property("jwt.realm").getString()

            install(StatusPages) {
                exception<Throwable> { cause ->
                    logger.error(cause) { "Build application finished with exception" }
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

            install(ContentNegotiation) {
                register(ContentType.Any, EmptyContentWrapper())
                serialization()
            }

            enableSwaggerSupport()

            install(Authentication) {
                jwt {
                    skipWhen { applicationCall ->
                        applicationCall.request.headers["Referer"]?.contains("openapi.json") ?: false
                    }
                    realm = jwtRealm
                    verifier(JwtConfig.verifier)
                    skipWhen { call -> call.request.headers["no-auth"]?.toBoolean() ?: false }
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

class EmptyContentWrapper(val srl: SerializationConverter = SerializationConverter(Json(DefaultJsonConfiguration))) : ContentConverter {
    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>) =
        if (context.subject.type == Unit::class) Unit
        else srl.convertForReceive(context)


    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ) = srl.convertForSend(context, contentType, value)

}
