package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*

class AgentUnregisterTest : E2ETest() {

    private val agentId = "unregisterAgent"

    @org.junit.jupiter.api.Test
    fun `Agent Unregister Test`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentId)) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(agentId).first shouldBe HttpStatusCode.OK
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