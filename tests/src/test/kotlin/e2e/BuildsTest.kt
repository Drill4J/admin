package com.epam.drill.admin.e2e

import com.epam.drill.common.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import kotlin.time.*
import io.ktor.http.*
import kotlin.test.*
import kotlin.time.seconds as secs

class BuildsTest : E2ETest() {

    private val agentId = "buildRenamingAgent"

    @Test
    fun `can add new builds`() {
        createSimpleAppWithUIConnection(
            agentStreamDebug = false,
            uiStreamDebug = false,
            timeout = 15.secs
        ) {
            val aw = AgentWrap(agentId)
            connectAgent(aw) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(aw.id) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`("DrillExtension1.class")
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 1

            }.reconnect(aw.copy(buildVersion = "0.1.2")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`("DrillExtension2.class")
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 2
            }.reconnect(aw.copy(buildVersion = "0.1.3")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 3
            }
        }

    }

}

