package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.plugin.api.message.*
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
                val statusResponse = "act".statusMessageResponse(StatusCodes.OK)
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
        lateinit var message: GroupedAgentsDto
        do {
            message = channel.receive()
            if (message.grouped.flatMap { it.agents }.all { it.activePluginsCount == 1 && it.status == AgentStatus.ONLINE })
                break
        } while (true)
    }


}
