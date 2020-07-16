package com.epam.drill.admin.e2e

import com.epam.drill.admin.agent.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlin.test.*


class AgentRegistrationTest : E2ETest() {

    private val agentId = "registerAgent"

    @Test
    fun `Agent should be registered`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentId, "0.1.0")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(agentId) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }
        }
    }

    @Test
    fun `agent must register without a description`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentId, "0.1.0")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(
                    agentId,
                    payload = AgentRegistrationDto(
                        name = "without description",
                        systemSettings = SystemSettingsDto(
                            packages = listOf("testPrefix")
                        ),
                        plugins = emptyList()
                    )
                ) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }
        }
    }

}
