package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*

class ToggleAgentTest : E2ETest() {

    private val agentId = "toggleAgent"

    @org.junit.jupiter.api.Test
    fun `Toggle Agent Test`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentId)) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                register(agentId).first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE

                toggleAgent(agentId)
                ui.getAgent()?.status shouldBe AgentStatus.OFFLINE
                toggleAgent(agentId)
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }
        }
    }
}