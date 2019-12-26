package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.endpoints.agent.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*


class AgentGroupTest : E2ETest() {

    @Ignore
    fun `emulate microservices registration`() {
        val wit = 0
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap("ag$wit", "0.1.$wit", "micro")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                launch {
                    register(
                        "ag$wit",
                        payload = AgentRegistrationInfo(
                            name = "first first",
                            description = "ad",
                            packagesPrefixes = listOf("testPrefix"),
                            plugins = emptyList()
                        )
                    ).first shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }
            val it = 1
            connectAgent(AgentWrap("ag$it", "0.1.$it", "micro")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                launch {
                    register(
                        "ag$it",
                        payload = AgentRegistrationInfo(
                            name = "first first",
                            description = "ad",
                            packagesPrefixes = listOf("testPrefix"),
                            plugins = emptyList()
                        )
                    ).first shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }


            uiWatcher { x ->

                println(x.receive().map { it.id to it.status to it.group })
                println(x.receive().map { it.id to it.status to it.group })
                println(x.receive().map { it.id to it.status to it.group })
                println(x.receive().map { it.id to it.status to it.group })

                val register = register("micro")
                register.first shouldBe HttpStatusCode.BadRequest
                println(register.second)

            }
        }
    }

}