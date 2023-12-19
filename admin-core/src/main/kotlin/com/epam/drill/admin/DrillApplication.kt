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
import com.epam.drill.admin.auth.config.DatabaseConfig
import com.epam.drill.admin.auth.principal.Role.ADMIN
import com.epam.drill.admin.auth.route.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.di.*
import com.epam.drill.admin.endpoints.admin.adminRoutes
import com.epam.drill.admin.endpoints.admin.adminWebSocketRoute
import com.epam.drill.admin.endpoints.admin.agentRoutes
import com.epam.drill.admin.endpoints.agent.agentWebSocketRoute
import com.epam.drill.admin.endpoints.plugin.pluginDispatcherRoutes
import com.epam.drill.admin.endpoints.plugin.pluginWebSocketRoute
import com.epam.drill.admin.group.groupRoutes
import com.epam.drill.admin.notification.notificationRoutes
import com.epam.drill.admin.service.requestValidatorRoutes
import com.epam.drill.admin.store.*
import com.epam.drill.admin.version.versionRoutes
import com.epam.dsm.*
import com.zaxxer.hikari.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import mu.*
import org.flywaydb.core.*
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import java.time.*


private val logger = KotlinLogging.logger {}

@Suppress("unused")
fun Application.module() {
    installPlugins()
    initDB()
    di {
        import(jwtServicesDIModule)
        import(apiKeyServicesDIModule)
        if (simpleAuthEnabled) import(simpleAuthDIModule)
        if (oauth2Enabled) import(oauthDIModule)
        import(authConfigDIModule)
        import(drillAdminDIModule)
    }

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
        drillAdminRoutes()
    }
    routing {
        if (oauth2Enabled) configureOAuthRoutes()
        route("/api") {
            if (simpleAuthEnabled) userAuthenticationRoutes()
            authenticate("jwt") {
                userProfileRoutes()
                userApiKeyRoutes()
            }
            authenticate("jwt", "api-key") {
                withRole(ADMIN) {
                    userManagementRoutes()
                    apiKeyManagementRoutes()
                }
            }
        }
    }
}


private fun Application.installPlugins() {
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

    interceptorForApplicationJson()

    enableSwaggerSupport()

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
        throw cause
    }
}

fun Routing.drillAdminRoutes() {
    uiConfigRoute()
    adminWebSocketRoute()
    agentRoutes()
    versionRoutes()
    requestValidatorRoutes()
    agentWebSocketRoute()
    pluginDispatcherRoutes()
    adminRoutes()
    groupRoutes()
    notificationRoutes()
    pluginWebSocketRoute()
}

private fun Application.initDB() {
    val host = drillDatabaseHost
    val port = drillDatabasePort
    val dbName = drillDatabaseName
    val userName = drillDatabaseUserName
    val password = drillDatabasePassword
    val maxPoolSize = drillDatabaseMaxPoolSize
    hikariConfig = HikariConfig().apply {
        this.driverClassName = "org.postgresql.Driver"
        this.jdbcUrl = "jdbc:postgresql://$host:$port/$dbName?reWriteBatchedInserts=true"
        this.username = userName
        this.password = password
        this.maximumPoolSize = maxPoolSize
        this.isAutoCommit = true
        this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        this.validate()
    }
    adminStore.createProcedureIfTableExist()
    val dataSource = HikariDataSource(hikariConfig)

    val flyway = Flyway.configure()
        .dataSource(dataSource)
        .schemas(adminStore.hikariConfig.schema)
        .baselineOnMigrate(true)
        .load()
    flyway.migrate()

    DatabaseConfig.init(dataSource)
}

lateinit var hikariConfig: HikariConfig

val Application.oauth2Enabled: Boolean
    get() = environment.config.config("drill.auth.oauth2")
        .propertyOrNull("enabled")?.getString()?.toBoolean() ?: false

val Application.simpleAuthEnabled: Boolean
    get() = environment.config.config("drill.auth.simple")
        .propertyOrNull("enabled")?.getString()?.toBoolean() ?: false