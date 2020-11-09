package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.channels.*
import kotlin.test.*

class PluginTest : E2EPluginTest() {

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
                val statusResponse = "act".statusMessageResponse(200)
                val statusesResponse: List<WithStatusCode> =
                    listOf(statusResponse, statusResponse, statusResponse)
                pluginAction("myActionForAllAgents", serviceGroup) { status, content ->
                    println("2")
                    status shouldBe HttpStatusCode.OK
                    content shouldBe serialize(statusesResponse)
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
