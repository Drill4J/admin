package com.epam.drill.admin.version

import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
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

    private val adminVersion = ClassLoader.getSystemClassLoader().run {
        getResourceAsStream("META-INF/drill/admin.version")?.readBytes()
    }?.decodeToString() ?: ""

    private val javaVersion = System.getProperty("java.version")

    init {
        app.routing { routes() }
    }

    private fun Routing.routes() {
        val versionMeta = "Get versions".responds(
            ok<VersionDto>(example("sample", VersionDto("0.1.0", javaVersion)))
        )
        get<Routes.Api.Version>(versionMeta) {
            call.respond(
                VersionDto(
                    admin = adminVersion,
                    java = javaVersion,
                    plugins = plugins.values.map { ComponentVersion(it.pluginBean.id, it.version) },
                    agents = agentManager.activeAgents.map {
                        ComponentVersion(
                            id = listOf(it.id, it.serviceGroup).filter(String::any).joinToString("@"),
                            version = it.agentVersion
                        )
                    }
                )
            )
        }
    }
}