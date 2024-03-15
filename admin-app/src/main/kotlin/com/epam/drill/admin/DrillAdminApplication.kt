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
package com.epam.drill.admin

import com.epam.drill.admin.auth.config.*
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.route.*
import com.epam.drill.admin.config.DatabaseConfig
import com.epam.drill.admin.config.uiConfigRoute
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.route.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import mu.KotlinLogging
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import org.kodein.di.singleton

private val logger = KotlinLogging.logger {}

fun Application.module() {
    installPlugins()
    di {
        bind<DatabaseConfig>() with singleton {
            DatabaseConfig(instance<Application>().environment.config.config("drill.database"))
        }
        import(jwtServicesDIModule)
        import(apiKeyServicesDIModule)
        if (simpleAuthEnabled) import(simpleAuthDIModule)
        if (oauth2Enabled) import(oauthDIModule)
        import(authConfigDIModule)
    }
    initDB()

    install(StatusPages) {
        authStatusPages()
        if (oauth2Enabled) oauthStatusPages()
        defaultStatusPages()
    }
    install(Authentication) {
        configureJwtAuthentication(closestDI())
        configureApiKeyAuthentication(closestDI())
        if (oauth2Enabled) configureOAuthAuthentication(closestDI())
    }
    routing {
        if (oauth2Enabled) configureOAuthRoutes()
        route("/api") {
            //UI
            uiConfigRoute()

            //Auth
            if (simpleAuthEnabled) userAuthenticationRoutes()
            authenticate("jwt") {
                userProfileRoutes()
                userApiKeyRoutes()
            }
            authenticate("jwt", "api-key") {
                withRole(Role.ADMIN) {
                    userManagementRoutes()
                    apiKeyManagementRoutes()
                }
            }
            authenticate("api-key") {
                tryApiKeyRoute()
            }

            //Data
            authenticate("api-key") {
                withRole(Role.USER, Role.ADMIN) {
                    intercept(ApplicationCallPipeline.Call) {
                        call.response.header("drill-internal", "true")
                        proceed()
                    }
                    putAgentConfig()
                    postCoverage()
                    postCLassMetadata()
                    postClassMetadataComplete()
                    postTestMetadata()
                    postRawJavaScriptCoverage(jsCoverageConverterAddress)
                }
            }
        }
    }
}

private fun Application.installPlugins() {
    install(CallLogging)
    install(Locations)

    install(ContentNegotiation) {
        json()
        register(ContentType.Application.ProtoBuf, SerializationConverter(ProtoBuf))
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

    install(RoleBasedAuthorization)
}

private fun StatusPages.Configuration.defaultStatusPages() {
    exception<Throwable> { cause ->
        logger.error(cause) { "Failed to process the request ${this.context.request.path()}" }
        call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
    }
}

private fun Application.initDB() {
    val databaseConfig by closestDI().instance<DatabaseConfig>()

    val host = databaseConfig.host
    val port = databaseConfig.port
    val dbName = databaseConfig.databaseName
    val userName = databaseConfig.username
    val password = databaseConfig.password
    val maxPoolSize = databaseConfig.maxPoolSize
    val hikariConfig = HikariConfig().apply {
        this.driverClassName = "org.postgresql.Driver"
        this.jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"
        this.username = userName
        this.password = password
        this.maximumPoolSize = maxPoolSize
        this.isAutoCommit = true
        this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        this.addDataSourceProperty("rewriteBatchedInserts", true)
        this.addDataSourceProperty("rewriteBatchedStatements", true)
        this.addDataSourceProperty("loggerLevel", "TRACE")
        this.validate()
    }
    val dataSource = HikariDataSource(hikariConfig)

    AuthDatabaseConfig.init(dataSource)
    RawDataWriterDatabaseConfig.init(dataSource)
}

val Application.oauth2Enabled: Boolean
    get() = environment.config.config("drill.auth.oauth2")
        .propertyOrNull("enabled")?.getString()?.toBoolean() ?: false

val Application.simpleAuthEnabled: Boolean
    get() = environment.config.config("drill.auth.simple")
        .propertyOrNull("enabled")?.getString()?.toBoolean() ?: false

val Application.jsCoverageConverterAddress: String
    get() = environment.config.config("drill.test2code")
        .propertyOrNull("jsCoverageConverterAddress")
        ?.getString()
        ?.takeIf { it.isNotBlank() }
        ?: "http://localhost:8092" // TODO think of default