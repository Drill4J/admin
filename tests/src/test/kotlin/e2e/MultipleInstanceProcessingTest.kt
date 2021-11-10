/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.e2e

import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.util.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlin.test.*
import kotlin.time.seconds as sec


class MultipleInstanceProcessingTest : E2ETest() {

    private val agentName = "java-agent"

    @Test
    fun `agent can have multiple instances`() {
        val instance = "instanceAgent"
        createSimpleAppWithUIConnection(timeout = 20.sec) {
            connectAgent(AgentWrap(agentName, "$instance:1"), {}) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(agentName) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                val agentInfo = ui.getAgent()
                agentInfo?.status shouldBe AgentStatus.ONLINE
                agentInfo?.instanceIds shouldBe setOf("$instance:1")
            }
            for (i in 2..5) {
                connectAgent(AgentWrap(agentName, "$instance:$i"), {}) { ui, _ ->
                    //todo if it doesn't invoke twice it will fail
                    val size = ui.getAgent()?.instanceIds?.size
                    logger.info {"Comparing count of instances '$i' cur $size..."}
                    ui.getAgent()?.instanceIds?.size shouldBe i
                }
            }
        }
    }

    @Test
    fun `agent should not be busy after new instance connect`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentName, "1"), {}) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(agentName) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                val agentInfo = ui.getAgent()
                agentInfo?.status shouldBe AgentStatus.ONLINE
                agentInfo?.instanceIds shouldBe setOf("1")
            }
            connectAgent(AgentWrap(agentName, "2"), {}) { ui, _ ->
                ui.getAgent()?.apply {
                    status shouldBe AgentStatus.ONLINE
                    instanceIds shouldBe setOf("1", "2")
                }
            }
            connectAgent(AgentWrap(agentName, "3"), {}) { ui, _ ->
                ui.getAgent()?.apply {
                    status shouldBe AgentStatus.ONLINE
                    instanceIds shouldBe setOf("1", "2", "3")
                }
            }
        }
    }

    @Test
    fun `not registered agent should not change status when new instance income `() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentName, "1")) { ui, _ ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
            }
            connectAgent(AgentWrap(agentName, "2")) { ui, _ ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
            }
        }
    }
}

