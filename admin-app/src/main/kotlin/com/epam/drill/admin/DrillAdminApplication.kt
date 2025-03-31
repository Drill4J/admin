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
import com.epam.drill.admin.config.dataSourceDIModule
import com.epam.drill.admin.metrics.route.metricsRoutes
import com.epam.drill.admin.common.route.commonStatusPages
import com.epam.drill.admin.config.SchedulerConfig
import com.epam.drill.admin.config.schedulerDIModule
import com.epam.drill.admin.metrics.config.*
import com.epam.drill.admin.route.rootRoute
import com.epam.drill.admin.route.uiConfigRoute
import com.epam.drill.admin.scheduler.DrillScheduler
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.dataRetentionPolicyJob
import com.epam.drill.admin.writer.rawdata.config.rawDataDIModule
import com.epam.drill.admin.writer.rawdata.route.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

fun Application.module() {
    val oauth2Enabled = oauth2Enabled
    val simpleAuthEnabled = simpleAuthEnabled
    di {
        import(dataSourceDIModule)
        import(schedulerDIModule)
        import(jwtServicesDIModule)
        import(apiKeyServicesDIModule)
        if (simpleAuthEnabled) import(simpleAuthDIModule)
        if (oauth2Enabled) import(oauthDIModule)
        import(authConfigDIModule)
        import(rawDataDIModule)
        import(metricsDIModule)
    }
    initDB()
    initScheduler()
    installPlugins()
    install(StatusPages) {
        authStatusPages()
        if (oauth2Enabled) oauthStatusPages()
        defaultStatusPages()
        commonStatusPages()
    }
    val di = closestDI()
    install(Authentication) {
        configureJwtAuthentication(di)
        configureApiKeyAuthentication(di)
        if (oauth2Enabled) configureOAuthAuthentication(di)
        roleBasedAuthentication()
    }
    routing {
        rootRoute()
        swaggerUI(path = "swagger", swaggerFile = "openapi.yml")
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

            //Admin
            authenticate("jwt", "api-key") {
                withRole(Role.ADMIN) {
                    userManagementRoutes()
                    apiKeyManagementRoutes()
                    settingsRoutes()
                }
            }

            //Metrics
            authenticate("jwt", "api-key") {
                tryApiKeyRoute()
                metricsRoutes()
            }

            //Data
            authenticate("api-key") {
                withRole(Role.USER, Role.ADMIN) {
                    intercept(ApplicationCallPipeline.Call) {
                        call.response.header("drill-internal", "true")
                        proceed()
                    }
                    dataIngestRoutes()
                }
            }
        }
    }
}

private fun Application.installPlugins() {
    install(CallLogging)
    install(Resources)

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        })
        protobuf()
    }
    install(Compression) {
        gzip()
        deflate()
    }

    install(CORS) {
        anyHost()
        allowCredentials = true
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        exposeHeader(HttpHeaders.Authorization)
        exposeHeader(HttpHeaders.ContentType)
    }
}

private fun StatusPagesConfig.defaultStatusPages() {
    exception<Throwable> { call, cause ->
        logger.error(cause) { "Failed to process the request ${call.request.path()}" }
        call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
    }
}

private fun Application.initDB() {
    val dataSource by closestDI().instance<DataSource>()

    AuthDatabaseConfig.init(dataSource)
    RawDataWriterDatabaseConfig.init(dataSource)
    MetricsDatabaseConfig.init(dataSource)
}

private fun Application.initScheduler() {
    val dataSource by closestDI().instance<DataSource>()
    val schedulerConfig by closestDI().instance<SchedulerConfig>()
    val scheduler by closestDI().instance<DrillScheduler>()

    scheduler.init(closestDI(), dataSource)
    environment.monitor.subscribe(ApplicationStopped) {
        scheduler.shutdown()
    }
    scheduler.start()
    scheduler.scheduleJob(refreshMethodsWithRulesViewJob, refreshMethodsWithRulesViewTrigger.withSchedule(schedulerConfig.refreshMatViewsSchedule).build())
    scheduler.scheduleJob(refreshMethodsCoverageViewJob, refreshMethodsCoverageViewTrigger.withSchedule(schedulerConfig.refreshMatViewsSchedule).build())
    scheduler.scheduleJob(refreshTestedBuildsComparisonViewJob, refreshTestedBuildsComparisonViewTrigger.withSchedule(schedulerConfig.refreshMatViewsSchedule).build())
    scheduler.scheduleJob(dataRetentionPolicyJob, schedulerConfig.retentionPoliciesTrigger)
}

val Application.oauth2Enabled: Boolean
    get() = environment.config.config("drill.auth.oauth2")
        .propertyOrNull("enabled")?.getString()?.toBoolean() ?: false

val Application.simpleAuthEnabled: Boolean
    get() = environment.config.config("drill.auth.simple")
        .propertyOrNull("enabled")?.getString()?.toBoolean() ?: false

val Application.jsCoverageConverterAddress: String
    get() = environment.config.config("drill.rawData")
        .propertyOrNull("jsCoverageConverterAddress")
        ?.getString()
        ?.takeIf { it.isNotBlank() }
        ?: "http://localhost:8092" // TODO think of default