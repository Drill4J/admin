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
package com.epam.drill.admin.endpoints.admin

import com.epam.drill.admin.agent.logging.LoggingHandler
import com.epam.drill.admin.agent.logging.defaultLoggingConfig
import com.epam.drill.admin.agent.toAgentBuildKey
import com.epam.drill.admin.api.LoggingConfigDto
import com.epam.drill.admin.api.routes.ApiRoot
import com.epam.drill.admin.api.routes.WsRoot
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.config.withRole
import com.epam.drill.admin.cache.CacheService
import com.epam.drill.admin.cache.impl.MapDBCacheService
import com.epam.drill.admin.endpoints.AgentManager
import com.epam.drill.admin.endpoints.BuildManager
import com.epam.drill.admin.endpoints.ErrorResponse
import com.epam.drill.admin.endpoints.TopicResolver
import com.epam.drill.admin.plugins.Plugins
import com.epam.drill.admin.version.AnalyticsToggleDto
import com.epam.drill.analytics.AnalyticService.ANALYTIC_DISABLE
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class DrillAdminEndpoints(override val di: DI) : DIAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val buildManager by instance<BuildManager>()
    private val loggingHandler by instance<LoggingHandler>()
    private val plugins by instance<Plugins>()
    private val cacheService by instance<CacheService>()
    private val topicResolver by instance<TopicResolver>()

    init {
        app.routing {

            authenticate("jwt", "basic") {
                withRole(Role.USER, Role.ADMIN) {
                put<ApiRoot.Agents.AgentLogging, LoggingConfigDto>(
                    "Configure agent logging levels"
                        .examples(
                            example("Agent logging configuration", defaultLoggingConfig)
                        )
                        .responds(
                            ok<Unit>(), notFound(), badRequest()
                        )
                ) { (_, agentId), loggingConfig ->
                    logger.debug { "Attempt to configure logging levels for agent with id $agentId" }
                    loggingHandler.updateConfig(agentId, loggingConfig)
                    logger.debug { "Successfully sent request for logging levels configuration for agent with id $agentId" }
                    call.respond(HttpStatusCode.OK, EmptyContent)
                }

                    get<ApiRoot.Cache.Stats>(
                        "Return cache stats"
                            .examples()
                            .responds(
                                ok<String>()
                            )
                    ) {
                        val cacheStats = (cacheService as? MapDBCacheService)?.stats() ?: emptyList()
                        call.respond(HttpStatusCode.OK, cacheStats)
                    }

                    get<ApiRoot.Cache.Clear>(
                        "Clear cache"
                            .examples()
                            .responds(
                                ok<String>()
                            )
                    ) {
                        (cacheService as? MapDBCacheService)?.clear()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            patch<ApiRoot.ToggleAnalytic, AnalyticsToggleDto>(
                "Toggle google analytics"
                    .examples(
                        example(
                            "analytics toggle request",
                            AnalyticsToggleDto(disable = true)
                        )
                    )
                    .responds(
                        ok<AnalyticsToggleDto>()
                    )
            ) { _, toggleDto ->
                System.setProperty(ANALYTIC_DISABLE, "${toggleDto.disable}")
                logger.info { "Analytics $ANALYTIC_DISABLE=${toggleDto.disable}" }
                topicResolver.sendToAllSubscribed(WsRoot.Analytics)
                call.respond(HttpStatusCode.OK, toggleDto)
            }
        }
    }
}
