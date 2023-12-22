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
package com.epam.drill.admin.version

import com.epam.drill.admin.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.*

class VersionEndpoints(override val di: DI) : DIAware {

    private val app by instance<Application>()
    private val plugins by instance<Plugins>()
    private val agentManager by instance<AgentManager>()
    private val buildManager by instance<BuildManager>()

    init {
        app.routing { versionRoutes() }
    }

    private fun Route.versionRoutes() {
        val versionMeta = "Get versions".responds(
            ok<VersionDto>(example("sample", VersionDto("0.1.0", adminVersion)))
        )
        get<ApiRoot.Version>(versionMeta) {
            call.respond(
                VersionDto(
                    admin = adminVersionDto.admin,
                    java = adminVersionDto.java,
                    plugins = plugins.values.map { ComponentVersion(it.pluginBean.id, it.version) },
                    agents = agentManager.activeAgents.flatMap { agentInfo ->
                        buildManager.instanceIds(agentInfo.id).map { (instanceId, _) ->
                            ComponentVersion(
                                id = listOf("${agentInfo.toAgentBuildKey()}/${instanceId}", agentInfo.groupId)
                                    .filter(String::any)
                                    .joinToString("@"),
                                version = agentInfo.build.agentVersion
                            )
                        }
                    }
                )
            )
        }
    }
}
