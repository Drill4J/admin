package com.epam.drill.e2e

import com.epam.drill.common.*
import io.kotlintest.*
import io.ktor.http.*
import kotlin.test.*

class AgentUnregisterTest : E2ETest() {

    private val agentId = "unregisterAgent"

    @Test
    fun `Agent Unregister Test`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentId)) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(agentId) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                unRegister(agentId)
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
            }
        }
    }
}