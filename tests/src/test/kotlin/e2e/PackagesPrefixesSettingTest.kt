package com.epam.drill.admin.e2e

import com.epam.drill.admin.api.agent.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.jupiter.api.*

class PackagesPrefixesSettingTest : E2ETest() {

    private val agentId = "ag02"

    @Test
    fun `Packages prefixes changing Test`() {
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

                changePackages(
                    agentId = agentId,
                    payload = SystemSettingsDto(
                        listOf("newTestPrefix"),
                        ""
                    )
                ) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                val agent2 = ui.getAgent()
                agent2?.status shouldBe AgentStatus.ONLINE
                agent2?.systemSettings?.packages?.first() shouldBe "newTestPrefix"
            }
        }
    }
}
