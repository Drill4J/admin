package com.epam.drill.e2e.plugin

import com.epam.drill.agentmanager.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.channels.*
import org.junit.jupiter.api.*

class PluginTest : E2EPluginTest() {

    @RepeatedTest(3) //some stress
    fun testE2ePluginAPI() {
        createSimpleAppWithPlugin<PTestStream> {
            connectAgent<Build1>("myServiceGroup") { _, _ ->
                pluginAction("x").first shouldBe HttpStatusCode.OK
                println("hi ag1")
            }
            connectAgent<Build1>("myServiceGroup") { _, _ ->
                println("hi ag2")
            }
            connectAgent<Build1>("myServiceGroup") { _, _ ->
                println("hi ag3")
            }
            connectAgent<Build1>("myServiceGroup") { _, _ ->
                println("hi ag4")
            }
            connectAgent<Build1>("myServiceGroup") { _, _ ->
                println("hi ag5")
            }
            uiWatcher { ch ->
                waitForMultipleAgents(ch)
                val (status, content) = pluginAction("myActionForAllAgents", "myServiceGroup")
                status shouldBe HttpStatusCode.OK
                content shouldBe "act"
            }
        }

    }

    private suspend fun waitForMultipleAgents(ch: Channel<Set<AgentInfoWebSocket>>) {
        lateinit var message: Set<AgentInfoWebSocket>
        do {
            message = ch.receive()
            if (message.all { it.activePluginsCount == 1 } && message.all { it.status == AgentStatus.ONLINE })
                break
        } while (true)
    }


}