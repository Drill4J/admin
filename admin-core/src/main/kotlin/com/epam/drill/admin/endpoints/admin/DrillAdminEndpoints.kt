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
import com.epam.drill.admin.api.routes.ApiRoot
import com.epam.drill.admin.api.routes.WsRoot
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.config.withRole
import com.epam.drill.admin.cache.CacheService
import com.epam.drill.admin.endpoints.AgentManager
import com.epam.drill.admin.endpoints.BuildManager
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
        withRole(Role.USER, Role.ADMIN) {
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
