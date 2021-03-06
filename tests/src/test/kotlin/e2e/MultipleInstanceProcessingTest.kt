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
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlin.test.*


class MultipleInstanceProcessingTest : E2ETest() {

    @Test
    fun `agent can have multiple instances`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap("myagent", "1", "0.1.1"), {}) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register("myagent") { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                val agentInfo = ui.getAgent()
                agentInfo?.status shouldBe AgentStatus.ONLINE
                agentInfo?.instanceIds shouldBe setOf("1")
                connectAgent(AgentWrap("myagent", "2", "0.1.1"), {}) { ui1, agent1 ->
                    ui1.getAgent()?.status shouldBe AgentStatus.ONLINE
                    ui1.getAgent()?.status shouldBe AgentStatus.BUSY

                    agent1.`get-set-packages-prefixes`()
                    agent1.`get-load-classes-datas`()
                    val agentInfo1 = ui1.getAgent()
                    agentInfo1?.status shouldBe AgentStatus.ONLINE
                    agentInfo1?.instanceIds shouldBe setOf("1", "2")
                }
            }
        }
    }
}
