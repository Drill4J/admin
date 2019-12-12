package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.endpoints.openapi.*
import com.epam.drill.testdata.*
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
                register(agentId).first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE

                changePackages(
                    agentId = agentId,
                    payload = SystemSettings(listOf("newTestPrefix"),"")
                ).first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                val agent2 = ui.getAgent()
                agent2?.status shouldBe AgentStatus.ONLINE
                agent2?.packagesPrefixes?.first() shouldBe "newTestPrefix"
            }
        }
    }
}
