package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*


class MultipleAgentRegistrationTest : E2ETest() {

    private val agentIdPrefix = "parallelRegister"

    @org.junit.jupiter.api.Test
    fun `4 Agents should be registered in parallel`() {
        createSimpleAppWithUIConnection {
            repeat(4) {
                connectAgent(AgentWrap("$agentIdPrefix$it", "0.1.$it")) { ui, agent ->
                    ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                    agent.getServiceConfig()?.sslPort shouldBe sslPort
                    register("$agentIdPrefix$it").first shouldBe HttpStatusCode.OK
                    ui.getAgent()?.status shouldBe AgentStatus.BUSY
                    agent.`get-set-packages-prefixes`()
                    agent.`get-load-classes-datas`()
                    ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                }
            }
        }
    }

}