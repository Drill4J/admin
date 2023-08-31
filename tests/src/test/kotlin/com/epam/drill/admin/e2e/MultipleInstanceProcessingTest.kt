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
import com.epam.drill.admin.waitUntil
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
                waitUntil { ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERED }
                waitUntil {
                    ui.getBuild()?.run {
                        buildStatus shouldBe BuildStatus.ONLINE
                        instanceIds shouldBe setOf("1")
                    }
                }
            }
            for (i in 2..5) {
                connectAgent(AgentWrap(agentName, "$i"), {}) { _, ui, _ ->
                    waitUntil {
                        ui.getBuild()?.run {
                            buildStatus shouldBe BuildStatus.ONLINE
                            instanceIds.size shouldBe i
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `agent should not be busy after new instance connect`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentName, "1"), {}) { _, ui, agent ->
                waitUntil { ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERED }
                waitUntil {
                    ui.getBuild()?.run {
                        buildStatus shouldBe BuildStatus.ONLINE
                        instanceIds shouldBe setOf("1")
                    }
                }

            }
            connectAgent(AgentWrap(agentName, "2"), {}) { _, ui, _ ->
                waitUntil {
                    ui.getBuild()?.run {
                        buildStatus shouldBe BuildStatus.ONLINE
                        instanceIds shouldBe setOf("1", "2")
                    }
                }
            }
            connectAgent(AgentWrap(agentName, "3"), {}) { _, ui, _ ->
                waitUntil {
                    ui.getBuild()?.run {
                        buildStatus shouldBe BuildStatus.ONLINE
                        instanceIds shouldBe setOf("1", "2", "3")
                    }
                }
            }
        }
    }

}

