/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin

import com.epam.drill.admin.config.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.jwt.user.source.*
import com.epam.drill.admin.kodein.*
import com.epam.dsm.*
import com.zaxxer.hikari.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.websocket.*
import mu.*
import ru.yandex.qatools.embed.postgresql.*
import ru.yandex.qatools.embed.postgresql.distribution.*
import java.time.*


//val drillHomeDir = File(System.getenv("DRILL_HOME") ?: "")

//val drillWorkDir = drillHomeDir.resolve("work")

val userSource: UserSource = UserSourceImpl()
val embeddedVersion = Version.V10_6
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
                converters()
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
        val host = drillDatabaseHost
        val port = drillDatabasePort
        val dbName = drillDatabaseName
        val userName = drillDatabaseUserName
        val password = drillDatabasePassword
        val maxPoolSize = drillDatabaseMaxPoolSize
        if (isDevMode) {
            logger.info { "starting dev mode for db..." }
            val postgres = EmbeddedPostgres(embeddedVersion)
            postgres.start(
                host,
                port,
                dbName,
                userName,
                password
            )
            environment.monitor.subscribe(ApplicationStopped) {
                logger.info { "close embedded db..." }//todo does it complete?
                postgres.close()
            }
        }
        DatabaseFactory.init(HikariDataSource(HikariConfig().apply {
            this.driverClassName = "org.postgresql.Driver"
            this.jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"
            this.username = userName
            this.password = password
            this.maximumPoolSize = maxPoolSize
            this.isAutoCommit = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            this.validate()
        }))
    }
)
