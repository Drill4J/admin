package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.storage.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import org.kodein.di.*
import org.kodein.di.generic.*


class ServiceGroupHandler(override val kodein: Kodein) : KodeinAware {

    private val app: Application by instance()
    private val serviceGroupManager: ServiceGroupManager by instance()
    private val wsTopic: WsTopic by instance()
    private val agentManager: AgentManager by instance()
    private val plugins: Plugins by instance()
    private val agentStorage: AgentStorage = agentManager.agentStorage

    init {
        app.routing {
            authenticate {
                val meta = "Update service group"
                    .examples(
                        example(
                            "serviceGroup",
                            ServiceGroup(
                                id = "some-group",
                                name = "Some Group"
                            )
                        )
                    ).responds(
                        ok<Unit>(),
                        notFound()
                    )
                put<Routes.Api.ServiceGroup, ServiceGroup>(meta) { _, group ->
                    val statusCode = when (serviceGroupManager.update(group)) {
                        null -> HttpStatusCode.NotFound
                        else -> HttpStatusCode.OK
                    }
                    call.respond(statusCode)
                }
            }
        }

        runBlocking {
            wsTopic {
                topic<WsRoutes.ServiceGroup> { (groupId) -> serviceGroupManager[groupId] }

                topic<WsRoutes.ServiceGroupPlugins> { (groupId) ->
                    agentManager.activeAgents
                        .filter { it.serviceGroup == groupId }
                        .plugins()
                        .mapToDto()
                }
            }
        }
    }

}
