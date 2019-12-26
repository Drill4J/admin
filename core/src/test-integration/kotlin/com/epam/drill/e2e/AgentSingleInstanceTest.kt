package com.epam.drill.e2e

import com.epam.drill.common.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class AgentSingleInstanceTest : E2ETest() {

    private val agentId = "instances"

    @Test
    fun `Agent shouldn't create more instances after reconnection`() {
        createSimpleAppWithUIConnection {
            val aw = AgentWrap(agentId)
            connectAgent(aw) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                launch {
                    register(agentId).first shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.apply {
                    status shouldBe AgentStatus.ONLINE
                    instanceIds.size shouldBe 1
                }
            }.reconnect(aw) { ui, _ ->
                ui.getAgent()?.apply {
                    status shouldBe AgentStatus.ONLINE
                    instanceIds.size shouldBe 1
                }
            }
        }
    }

}
