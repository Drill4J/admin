package com.epam.drill.admin.e2e

import com.epam.drill.common.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.test.*


class MultipleInstanceProcessingTest : E2ETest() {


    @Test
    fun `agent can have multiple instances`() {
        createSimpleAppWithUIConnection {
            val latch1 = Channel<Int>()
            val latch2 = Channel<Int>()
            lateinit var thisIsAProblemOfTestFrameWork: AdminUiChannels
            connectAgent(AgentWrap("myagent", "1", "0.1.1"), {}) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register("myagent") { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                callAsync {
                    latch1.send(1)
                }
                latch2.receive()
                delay(500)
                thisIsAProblemOfTestFrameWork.getAgent()?.instanceIds?.first() shouldBe "1"
            }
            connectAgent(AgentWrap("myagent", "2", "0.1.1"), {
                latch1.receive()
            }) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getAgent()?.status shouldBe AgentStatus.BUSY

                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                callAsync {
                    latch2.send(1)
                }
                thisIsAProblemOfTestFrameWork = ui //todo
            }
        }
    }

}
