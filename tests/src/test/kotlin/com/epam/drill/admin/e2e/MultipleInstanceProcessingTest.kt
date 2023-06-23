/**
 * Copyright 2020 - 2022 EPAM Systems
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
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlin.test.*


class MultipleInstanceProcessingTest : E2ETest() {

    private val agentName = "java-agent"

    @Test
    fun `agent can have multiple instances`() {
        val instance = "instanceAgent"
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentName, "1"), {}) { _, ui, agent ->
                ui.getAgent()?.agentStatus shouldBe AgentStatus.NOT_REGISTERED
                ui.getBuild()?.buildStatus shouldBe BuildStatus.ONLINE
                register(agentName) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERING
                ui.getBuild()?.buildStatus shouldBe BuildStatus.BUSY
                agent.`get-set-packages-prefixes`()
                ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERED
                ui.getBuild()?.run {
                    buildStatus shouldBe BuildStatus.ONLINE
                    instanceIds shouldBe setOf("1")
                }
            }
            for (i in 2..5) {
                connectAgent(AgentWrap(agentName, "$i"), {}) { _, ui, _ ->
                    ui.getBuild()
                    ui.getBuild()?.run {
                        buildStatus shouldBe BuildStatus.ONLINE
                        instanceIds.size shouldBe i
                    }
                }
            }
        }
    }

    @Test
    fun `agent should not be busy after new instance connect`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentName, "1"), {}) { _, ui, agent ->
                ui.getAgent()?.agentStatus shouldBe AgentStatus.NOT_REGISTERED
                ui.getBuild()?.buildStatus shouldBe BuildStatus.ONLINE
                register(agentName) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERING
                ui.getBuild()?.buildStatus shouldBe BuildStatus.BUSY
                agent.`get-set-packages-prefixes`()
                val agentInfo = ui.getAgent()
                agentInfo?.agentStatus shouldBe AgentStatus.REGISTERED
                ui.getBuild()?.run {
                    buildStatus shouldBe BuildStatus.ONLINE
                    instanceIds shouldBe setOf("1")
                }
            }
            connectAgent(AgentWrap(agentName, "2"), {}) { _, ui, _ ->
                ui.getBuild()
                ui.getBuild()?.run {
                    buildStatus shouldBe BuildStatus.ONLINE
                    instanceIds shouldBe setOf("1", "2")
                }
            }
            connectAgent(AgentWrap(agentName, "3"), {}) { _, ui, _ ->
                ui.getBuild()
                ui.getBuild()?.run {
                    buildStatus shouldBe BuildStatus.ONLINE
                    instanceIds shouldBe setOf("1", "2", "3")
                }
            }
        }
    }

    @Test
    fun `not registered agent should not change status when new instance income `() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentName, "1")) { _, ui, _ ->
                ui.getAgent()?.agentStatus shouldBe AgentStatus.NOT_REGISTERED
            }
            connectAgent(AgentWrap(agentName, "2")) { _, ui, _ ->
                ui.getAgent()?.agentStatus shouldBe AgentStatus.NOT_REGISTERED
            }
        }
    }
}

