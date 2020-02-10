package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.servicegroup.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.plugin.api.message.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.channels.*
import org.junit.jupiter.api.*

class PluginTest : E2EPluginTest() {

    @RepeatedTest(10)
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
            connectAgent<Build1>(serviceGroup) { _, _ ->
                println("hi ag4")
            }
            connectAgent<Build1>(serviceGroup) { _, _ ->
                println("hi ag5")
            }
            uiWatcher { ch ->
                waitForMultipleAgents(ch)
                println("1")
                val expectedContent = StatusMessage.serializer() stringify StatusMessage(StatusCodes.OK, "act")
                pluginAction("myActionForAllAgents", serviceGroup) { status, content ->
                    println("2")
                    status shouldBe HttpStatusCode.OK
                    content shouldBe expectedContent
                }
                println("3")
            }
        }

    }

    private suspend fun waitForMultipleAgents(ch: Channel<GroupedAgentsDto>) {
        lateinit var message: GroupedAgentsDto
        do {
            message = ch.receive()
            if (message.grouped.flatMap { it.agents }.all { it.activePluginsCount == 1 && it.status == AgentStatus.ONLINE })
                break
        } while (true)
    }


}
