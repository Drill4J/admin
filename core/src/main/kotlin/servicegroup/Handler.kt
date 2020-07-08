package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*


class ServiceGroupHandler(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val serviceGroupManager by instance<ServiceGroupManager>()
    private val wsTopic by instance<WsTopic>()
    private val plugins by instance<Plugins>()
    private val pluginCache by instance<PluginCache>()
    private val agentManager by instance<AgentManager>()

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
                put<ApiRoot.ServiceGroup, ServiceGroup>(meta) { _, group ->
                    val statusCode = when (serviceGroupManager.update(group)) {
                        null -> HttpStatusCode.NotFound
                        else -> HttpStatusCode.OK
                    }
                    call.respond(statusCode)
                }
            }

            get<ApiRoot.ServiceGroup.Plugin.Data> { (pluginParent, dataType) ->
                val (group, pluginId) = pluginParent
                val groupId = group.serviceGroupId
                logger.trace { "Get plugin data, groupId=${groupId}, pluginId=${pluginId}, dataType=$dataType" }
                val (statusCode, response) = if (pluginId in plugins) {
                    val serviceGroup: List<AgentEntry> = agentManager.serviceGroup(groupId)
                    if (serviceGroup.any()) {
                        val key = GroupSubscription(groupId).toKey("/service-group/data/$dataType")
                        pluginCache[key].toStatusResponsePair()
                    } else HttpStatusCode.NotFound to ErrorResponse(
                        "service group $groupId not found"
                    )
                } else HttpStatusCode.NotFound to ErrorResponse("plugin '$pluginId' not found")
                logger.trace { response }
                call.respond(statusCode, response)
            }
        }

        runBlocking {
            wsTopic {
                topic<WsRoutes.ServiceGroup> { (groupId) -> serviceGroupManager[groupId] }

                topic<WsRoutes.ServiceGroupPlugins> { (groupId) ->
                    agentManager.run {
                        val agents = activeAgents.filter { it.serviceGroup == groupId }
                        plugins.values.ofAgents(agents).mapToDto()
                    }
                }

            }
        }
    }
}
