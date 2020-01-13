package com.epam.drill.admin.servicegroup

import com.epam.drill.endpoints.*
import com.epam.drill.plugins.*
import com.epam.drill.router.*
import com.epam.drill.storage.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
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
                put<Routes.Api.ServiceGroup.Update, ServiceGroup>(meta) { _, group ->
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

                topic<WsRoutes.ServiceGroupPlugin> { (groupId, pluginId) ->
                    val summaries = agentStorage.values
                        .filter { it.agent.serviceGroup == groupId }
                        .map {
                            val adminData = agentManager.adminData(it.agent.id)
                            val plugin = plugins[pluginId]
                            val pluginData = plugin?.summaryOf(it) ?: JsonNull
                            it.toPluginSummaryDto(adminData, pluginData)
                        }
                    ServiceGroupSummaryDto(
                        name = serviceGroupManager[groupId]?.name ?: "",
                        summaries = summaries,
                        count = summaries.count(),
                        aggregatedData = JsonNull //TODO aggregation
                    )
                }
            }
        }
    }

    private suspend fun Plugin.summaryOf(entry: AgentEntry): Any {
        val adminPart = agentManager.instantiateAdminPluginPart(entry, pluginClass, pluginBean.id)
        return adminPart.getPluginData(emptyMap())
    }
}
