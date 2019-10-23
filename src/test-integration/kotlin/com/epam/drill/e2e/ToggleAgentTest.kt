package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.*

class ToggleAgentTest : AbstractE2ETest() {

    @Test(timeout = 10000)
    fun `Toggle Agent Test`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap("ag1")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                register("ag1").first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-data`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE

                toggleAgent("ag1")
                ui.getAgent()?.status shouldBe AgentStatus.OFFLINE
                toggleAgent("ag1")
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }
        }
    }
}