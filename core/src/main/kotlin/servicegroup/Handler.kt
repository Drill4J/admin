package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.common.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*


class ServiceGroupHandler(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val serviceGroupManager by instance<ServiceGroupManager>()
    private val plugins by instance<Plugins>()
    private val pluginCache by instance<PluginCache>()
    private val agentManager by instance<AgentManager>()

    init {
        app.routing {
            authenticate {
                val meta = "Update service group"
                    .examples(
                        example("serviceGroup", ServiceGroupUpdateDto(name = "Some Group"))
                    ).responds(ok<Unit>(), notFound())
                put<ApiRoot.ServiceGroup, ServiceGroupUpdateDto>(meta) { (_, id), info ->
                    val statusCode = serviceGroupManager[id]?.let { group ->
                        serviceGroupManager.update(
                            group.copy(
                                name = info.name,
                                description = info.description,
                                environment = info.environment
                            )
                        )
                        HttpStatusCode.OK
                    } ?: HttpStatusCode.NotFound
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
                        pluginCache[key]?.let {
                            HttpStatusCode.OK to it
                        } ?: HttpStatusCode.NotFound to EmptyContent
                    } else HttpStatusCode.NotFound to ErrorResponse(
                        "service group $groupId not found"
                    )
                } else HttpStatusCode.NotFound to ErrorResponse("plugin '$pluginId' not found")
                logger.trace { response }
                call.respond(statusCode, response)
            }

            authenticate {
                val meta = "Update system settings of service group"
                    .examples(
                        example(
                            "systemSettings",
                            SystemSettingsDto(listOf("some package prefixes"), "some session header name")
                        )
                    ).responds(
                        ok<Unit>(),
                        notFound()
                    )
                put<ApiRoot.ServiceGroup.SystemSettings, SystemSettingsDto>(meta) { (group), systemSettings ->
                    val id: String = group.serviceGroupId
                    val status: HttpStatusCode = serviceGroupManager[id]?.let { serviceGroup ->
                        if (systemSettings.packages.all { it.isNotBlank() }) {
                            val agentInfos: List<AgentInfo> = agentManager.serviceGroup(id).map { it.agent }
                            val updatedAgentIds = agentManager.updateSystemSettings(agentInfos, systemSettings)
                            serviceGroupManager.updateSystemSettings(serviceGroup, systemSettings)
                            if (updatedAgentIds.count() < agentInfos.count()) {
                                logger.error {
                                    """Service group $id: not all agents updated successfully.
                                        |Failed agents: ${agentInfos - updatedAgentIds}.
                                    """.trimMargin()
                                }
                            } else logger.debug { "Service group $id: updated agents $updatedAgentIds." }
                            HttpStatusCode.OK
                        } else HttpStatusCode.BadRequest
                    } ?: HttpStatusCode.NotFound
                    call.respond(status)
                }
            }
        }
    }
}
