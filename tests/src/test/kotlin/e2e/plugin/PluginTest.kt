package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.builtins.*
import kotlin.test.*

class PluginTest : E2EPluginTest() {

    @Test
    fun `reconnect - same build`() {
        createSimpleAppWithPlugin<PTestStream> {
            connectAgent<Build1> { _, build ->
                println(build)
                pluginAction("myActionForAllAgents") { status, content ->
                    println(content)
                    status shouldBe HttpStatusCode.OK
                }.join()
            }.reconnect<Build1> { plugUi, build ->
                println("Reconnected agentId=${plugUi.info.agentId}, buildVersion=${build.version}")
            }
        }
    }

    @Test
    fun `reconnect - new build`() {
        createSimpleAppWithPlugin<PTestStream>(
            uiStreamDebug = true,
            agentStreamDebug = true
        ) {
            connectAgent<Build1> { plugUi, _ ->
                plugUi.processedData.receive()
                pluginAction("myActionForAllAgents") { status, content ->
                    println(content)
                    status shouldBe HttpStatusCode.OK
                }.join()
            }.reconnect<Build2> { plugUi, build ->
                plugUi.processedData.receive()
                println("Reconnected agentId=${plugUi.info.agentId}, buildVersion=${build.version}")
            }
        }
    }


    @Test
    fun `test e2e plugin API for service group`() {
        val serviceGroup = "myServiceGroup"
        createSimpleAppWithPlugin<PTestStream> {
            connectAgent<Build1>(serviceGroup) { _, _ ->
                println("hi ag1")
            }
            connectAgent<Build1>(serviceGroup) { _, _ ->
                println("hi ag2")
            }
            connectAgent<Build1>(serviceGroup) { _, _ ->
                println("hi ag3")
            }
            uiWatcher { channel ->
                waitForMultipleAgents(channel)
                println("1")
                val statusResponse = StatusMessageResponse(
                    code = 200,
                    message = "act"
                )
                val statusResponses: List<WithStatusCode> =
                    listOf(statusResponse, statusResponse, statusResponse)
                pluginAction("myActionForAllAgents", serviceGroup) { status, content ->
                    println("2")
                    status shouldBe HttpStatusCode.OK
                    content shouldBe (ListSerializer(WithStatusCode.serializer()) stringify statusResponses)
                }
                println("3")
            }
        }

    }

    private suspend fun waitForMultipleAgents(channel: Channel<GroupedAgentsDto>) {
        while (true) {
            val message = channel.receive()
            val groupedAgents = message.grouped.flatMap { it.agents }
            if (groupedAgents.all { it.activePluginsCount == 1 && it.status == AgentStatus.ONLINE }) {
                break
            }
        }
    }
}
