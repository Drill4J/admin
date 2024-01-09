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

import com.epam.drill.admin.api.routes.ApiRoot
import com.epam.drill.admin.auth.config.*
import com.epam.drill.admin.auth.config.DatabaseConfig
import com.epam.drill.admin.auth.principal.Role.ADMIN
import com.epam.drill.admin.auth.route.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.di.*
import com.epam.drill.admin.endpoints.admin.adminRoutes
import com.epam.drill.admin.endpoints.admin.adminWebSocketRoute
import com.epam.drill.admin.endpoints.admin.agentRoutes
import com.epam.drill.admin.endpoints.admin.ensureQueryParams
import com.epam.drill.admin.endpoints.agent.agentWebSocketRoute
import com.epam.drill.admin.endpoints.plugin.pluginDispatcherRoutes
import com.epam.drill.admin.endpoints.plugin.pluginWebSocketRoute
import com.epam.drill.admin.group.groupRoutes
import com.epam.drill.admin.notification.notificationRoutes
import com.epam.drill.admin.service.requestValidatorRoutes
import com.epam.drill.admin.store.*
import com.epam.drill.admin.version.versionRoutes
import com.epam.drill.plugins.test2code.multibranch.service.generateHtmlTable
import com.epam.drill.plugins.test2code.multibranch.service.getNewRisks
import com.epam.dsm.*
import com.zaxxer.hikari.*
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.JsonElement
import kotlinx.html.*
import mu.*
import org.flywaydb.core.*
import org.kodein.di.DI
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import java.time.*


private val logger = KotlinLogging.logger {}

@Suppress("unused")
fun Application.module() {
    when (environment.config.config("drill.auth").getAuthType()) {
        AuthType.SIMPLE -> moduleWithSimpleAuth()
        AuthType.OAUTH2 -> moduleWithOAuth2()
        AuthType.SIMPLE_AND_OAUTH2 -> moduleWithSimpleAuthAndOAuth2()
    }
}

@Suppress("unused")
fun Application.moduleWithSimpleAuth() {
    install(StatusPages) {
        simpleAuthStatusPages()
        defaultStatusPages()
    }
    installPlugins()
    initDB()

    di {
        import(drillAdminDIModule)
        import(simpleAuthDIModule)
    }

    install(Authentication) {
        configureJwtAuthentication(closestDI())
        configureBasicAuthentication(closestDI())
    }

    routing {
        get<ApiRoot.GetReport>(
            "Get report"
                .responds(
                    ok<List<JsonElement>>()
                )
        ) {
            call.request.ensureQueryParams("newInstanceId", "oldInstanceId")
            val newInstanceId = call.request.queryParameters["newInstanceId"] ?: ""
            val oldInstanceId = call.request.queryParameters["oldInstanceId"] ?: ""
            val report = getNewRisks(newInstanceId, oldInstanceId)
            call.respond(HttpStatusCode.OK, report)
        }
        get<ApiRoot.GetReportHTML>(
            "Get report HTML"
                .responds(
                    ok<String>()
                )
        ) {
            call.request.ensureQueryParams("newInstanceId", "oldInstanceId")
            val newInstanceId = call.request.queryParameters["newInstanceId"] ?: ""
            val oldInstanceId = call.request.queryParameters["oldInstanceId"] ?: ""
            val report = generateHtmlTable(getNewRisks(newInstanceId, oldInstanceId))
            call.respondHtml(HttpStatusCode.OK) {
                head {
                    title {
                        +"Report"
                    }
                    link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/tailwindcss@2.2.19/dist/tailwind.min.css")
                    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap")
                    // TODO figure out why DSL style {...} does not work (it gets encoded)
                    unsafe {
                        raw(
                            """
                            <style>
                                body > * {
                                    text-align: left;
                                }
                                table > td {
                                    border: 1px solid black;
                                }
                            </style>
                            """.trimIndent()
                        )
                    }
                }
                body {
                    h1("font-bold") {
                        +"Risks new"
                    }
                    div("mt-4") {
                        unsafe { raw(report) }
                    }
                }
            }

        }
        drillAdminRoutes()
        loginRoute()
        route("/api") {
            userAuthenticationRoutes()
            authenticate("jwt") {
                userProfileRoutes()
            }
            authenticate("jwt", "basic") {
                withRole(ADMIN) {
                    userManagementRoutes()
                }
            }
        }
    }
}

@Suppress("unused")
fun Application.moduleWithOAuth2() {
    install(StatusPages) {
        simpleAuthStatusPages()
        defaultStatusPages()
    }
    installPlugins()
    initDB()
    di {
        import(drillAdminDIModule)
        import(oauthDIModule)
    }
    install(Authentication) {
        configureJwtAuthentication(closestDI())
        configureOAuthAuthentication(closestDI())
        configureBasicStubAuthentication()
    }
    routing {
        drillAdminRoutes()
        configureOAuthRoutes()
        route("/api") {
            authenticate("jwt") {
                userInfoRoute()
            }
            authenticate("jwt") {
                withRole(ADMIN) {
                    getUsersRoute()
                    getUserRoute()
                    editUserRoute()
                    deleteUserRoute()
                    blockUserRoute()
                    unblockUserRoute()
                }
            }
        }
    }
}

@Suppress("unused")
fun Application.moduleWithSimpleAuthAndOAuth2() {
    install(StatusPages) {
        simpleAuthStatusPages()
        defaultStatusPages()
    }
    installPlugins()
    initDB()
    di {
        import(drillAdminDIModule)
        import(simpleWithOAuth2DIModule)
    }
    install(Authentication) {
        configureJwtAuthentication(closestDI())
        configureOAuthAuthentication(closestDI())
        configureBasicAuthentication(closestDI())
    }
    routing {
        drillAdminRoutes()
        configureOAuthRoutes()
        route("/api") {
            userAuthenticationRoutes()
            authenticate("jwt") {
                userProfileRoutes()
            }
            authenticate("jwt") {
                withRole(ADMIN) {
                    userManagementRoutes()
                }
            }
        }
    }
}

val simpleWithOAuth2DIModule = DI.Module("simpleWithOAuth2") {
    userRepositoriesConfig()
    userServicesConfig()
    configureJwtDI()
    configureOAuthDI()
    configureSimpleAuthDI()
    bindAuthConfig()
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

    // TODO it might be beneficial to use separate JDBC driver instances for auth module and for test2code ops
    //  why: auth module does not require batched operations, hence db interop code can be simplified
    //  e.g. autoCommit might prevent batching, but is very handy for auth-related queries
    hikariConfig = HikariConfig().apply {
        this.driverClassName = "org.postgresql.Driver"
        this.jdbcUrl = "jdbc:postgresql://$host:$port/$dbName?reWriteBatchedInserts=true"
        this.username = userName
        this.password = password
        this.maximumPoolSize = maxPoolSize
        this.isAutoCommit = true
        this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"

        // cleaner way to set connection properties
        // see https://jdbc.postgresql.org/documentation/use/#connection-parameters
//        this.addDataSourceProperty("reWriteBatchedInserts", true)

        // TODO investigate best-performing batched insertion
        // 1. use prepared statement
        // 2. set reWriteBatchedInserts to true
        //      - it might _not_ work when isAutoCommit set to true (investigate)
        // 3. set Statement.RETURN_GENERATED_KEYS to false
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

    // auth db config
    DatabaseConfig.init(dataSource)

    // test2code raw data db config
    com.epam.drill.plugins.test2code.multibranch.rawdata.config.DatabaseConfig.init(dataSource)
}

lateinit var hikariConfig: HikariConfig
