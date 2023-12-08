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
package com.epam.drill.admin.endpoints.instance

import com.epam.drill.admin.agent.AgentInfo
import com.epam.drill.admin.api.agent.BuildStatus
import com.epam.drill.admin.endpoints.AgentManager
import com.epam.drill.admin.endpoints.BuildManager
import com.epam.drill.admin.endpoints.plugin.PluginDispatcher
import com.epam.drill.common.agent.configuration.AgentConfig
import com.epam.drill.plugins.test2code.TEST2CODE_PLUGIN
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import com.epam.drill.plugins.test2code.common.transport.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.locations.put
import io.ktor.locations.post
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*

private val logger = KotlinLogging.logger {}

@Location("/api/agents/{agentId}")
data class Agents(val agentId: String) {

    @Location("/builds/{buildVersion}/coverage")
    data class Coverage(val parent: Agents, val buildVersion: String)

    @Location("/builds/{buildVersion}/class-metadata")
    data class ClassMetadata(val parent: Agents, val buildVersion: String)

    @Location("/builds/{buildVersion}/class-metadata/complete")
    data class ClassMetadataComplete(val parent: Agents, val buildVersion: String)
}

class AgentInstanceEndpoints(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val buildManager by instance<BuildManager>()
    private val pd by instance<PluginDispatcher>()

    init {
        app.routing {
            registerAgentInstanceRoute()
            sendCoverageRoute()
            sendClassMetadataRoute()
            completeSendingClassMetadataRoute()
        }
    }

    private fun Route.registerAgentInstanceRoute() {
        put<Agents> {
            val agentConfig = call.receive<AgentConfig>()
            val agentInfo: AgentInfo = withContext(Dispatchers.IO) {
                agentManager.attach(agentConfig)
            }
            processPluginData(agentInfo, InitInfo())
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.sendCoverageRoute() {
        post<Agents.Coverage> { params ->
            handleAgentRequest(params.parent.agentId, params.buildVersion) { agentInfo ->
                val data = call.receive<CoverageData>()
                processPluginData(agentInfo, data.toCoverDataPart())
            }
        }
    }

    private fun Route.sendClassMetadataRoute() {
        post<Agents.ClassMetadata> { params ->
            handleAgentRequest(params.parent.agentId, params.buildVersion) { agentInfo ->
                val data = call.receive<ClassMetadata>()
                processPluginData(agentInfo, data.toInitDataPart())
            }
        }
    }

    private fun Route.completeSendingClassMetadataRoute() {
        post<Agents.ClassMetadataComplete> { params ->
            handleAgentRequest(params.parent.agentId, params.buildVersion) { agentInfo ->
                processPluginData(agentInfo, Initialized())
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handleAgentRequest(
        agentId: String,
        buildVersion: String,
        handler: suspend (AgentInfo) -> Any
    ) {
        agentManager.getOrNull(agentId)
            ?.let { agentInfo ->
                when (agentInfo.build.version) {
                    buildVersion -> {
                        val response = handler(agentInfo)
                        call.respond(HttpStatusCode.OK, response)
                    }
                    else -> {
                        logger.error { "The active build version of agent $agentId is ${agentInfo.build.version}, but the request has $buildVersion build version!" }
                        call.respond(HttpStatusCode.Conflict)
                    }
                }
            } ?: call.respond(HttpStatusCode.NotFound).also {
            logger.error { "Agent $agentId is not attached!" }
        }
    }

    private suspend fun <T : CoverMessage> processPluginData(
        agentInfo: AgentInfo,
        data: T
    ) {
        pd.processPluginData(agentInfo, TEST2CODE_PLUGIN, data)
    }
}

private fun ClassMetadata.toInitDataPart() = InitDataPart(
    astEntities = astEntities
)

private fun CoverageData.toCoverDataPart() = CoverDataPart(
    data = execClassData
)