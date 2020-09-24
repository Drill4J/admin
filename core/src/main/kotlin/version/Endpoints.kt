package com.epam.drill.admin.version

import com.epam.drill.admin.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.*
import org.kodein.di.generic.*

class VersionEndpoints(override val kodein: Kodein) : KodeinAware {

    private val app by instance<Application>()
    private val plugins by instance<Plugins>()
    private val agentManager by instance<AgentManager>()

    init {
        app.routing { routes() }
    }

    private fun Routing.routes() {
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
                        agentManager.instanceIds(agentInfo.id).map { (instanceId, _) ->
                            ComponentVersion(
                                id = listOf("${agentInfo.id}/${instanceId}", agentInfo.serviceGroup)
                                    .filter(String::any)
                                    .joinToString("@"),
                                version = agentInfo.agentVersion
                            )
                        }
                    }
                )
            )
        }
    }
}
