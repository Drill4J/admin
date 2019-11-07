package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*


class AgentRegistrationTest : AbstractE2ETest() {

    @org.junit.jupiter.api.Test
    fun `Agent should be registered`() {
        val it = 0
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap("ag$it", "0.1.$it")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                register("ag$it").first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }
        }
    }

}