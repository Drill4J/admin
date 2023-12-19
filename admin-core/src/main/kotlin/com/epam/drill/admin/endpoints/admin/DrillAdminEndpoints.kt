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
import com.epam.drill.admin.api.agent.BuildStatus
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
import com.epam.drill.admin.plugin.TogglePayload
import com.epam.drill.admin.plugins.Plugins
import com.epam.drill.admin.version.AnalyticsToggleDto
import com.epam.drill.analytics.AnalyticService.ANALYTIC_DISABLE
import com.epam.drill.common.ws.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

fun Routing.adminRoutes() {
    val logger = KotlinLogging.logger {}

    val agentManager by closestDI().instance<AgentManager>()
    val buildManager by closestDI().instance<BuildManager>()
    val loggingHandler by closestDI().instance<LoggingHandler>()
    val plugins by closestDI().instance<Plugins>()
    val cacheService by closestDI().instance<CacheService>()
    val topicResolver by closestDI().instance<TopicResolver>()


    authenticate("jwt", "api-key") {
        withRole(Role.USER) {
            post<ApiRoot.Agents.ToggleAgent>(
                "Agent Toggle StandBy"
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
            ) { params ->
                val (_, agentId) = params
                logger.info { "Toggle agent $agentId" }
                val (status, response) = agentManager[agentId]?.let { agentInfo ->
                    val status = buildManager.buildStatus(agentId)
                    val agentBuildKey = agentInfo.toAgentBuildKey()
                    when (status) {
                        BuildStatus.OFFLINE -> BuildStatus.ONLINE
                        BuildStatus.ONLINE -> BuildStatus.OFFLINE
                        else -> null
                    }?.let { newStatus ->
                        buildManager.instanceIds(agentId).forEach { (id, value) ->
                            buildManager.updateInstanceStatus(agentBuildKey, id, newStatus)
                            val toggleValue = newStatus == BuildStatus.ONLINE
                            agentInfo.plugins.map { pluginId ->
                                value.agentWsSession.sendToTopic<Communication.Plugin.ToggleEvent, TogglePayload>(
                                    TogglePayload(pluginId, toggleValue)
                                )
                            }.forEach { it.await() } //TODO coroutine scope (supervisor)
                        }
                        buildManager.notifyBuild(agentBuildKey)
                        logger.info { "Agent $agentId toggled, new build status - $newStatus." }
                        HttpStatusCode.OK to EmptyContent
                    } ?: (HttpStatusCode.Conflict to ErrorResponse(
                        "Cannot toggle agent $agentId on status $status"
                    ))
                } ?: (HttpStatusCode.NotFound to EmptyContent)
                call.respond(status, response)
            }


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
