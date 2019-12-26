package com.epam.drill.e2e

import com.epam.drill.common.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*


class MultipleAgentRegistrationTest : E2ETest() {

    private val agentIdPrefix = "parallelRegister"

    @Ignore
    fun `3 Agents should be registered in parallel`() {
        createSimpleAppWithUIConnection {
            repeat(3) {
                connectAgent(AgentWrap("$agentIdPrefix$it", "0.1.$it")) { ui, agent ->
                    ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                    launch {
                        register("$agentIdPrefix$it").first shouldBe HttpStatusCode.OK
                    }
                    ui.getAgent()?.status shouldBe AgentStatus.BUSY
                    agent.`get-set-packages-prefixes`()
                    agent.`get-load-classes-datas`()
                    ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                }
            }
        }
    }

}