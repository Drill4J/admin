package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.servicegroup.*
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
                println("5")
                pluginAction("x") { code, _ ->
                    code shouldBe HttpStatusCode.OK
                    println("hi ag1")
                }
                println("6")
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
                println("1")
                pluginAction("myActionForAllAgents", "myServiceGroup") { status, content ->
                    println("2")
                    status shouldBe HttpStatusCode.OK
                    content shouldBe "act"
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
