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
import java.util.concurrent.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.seconds as sec

class AgentSingleInstanceTest : E2ETest() {

    private val agentId = "instances"

    @Test
    fun `Agent shouldn't create more instances after reconnection`() {
        createSimpleAppWithUIConnection(timeout = Duration.seconds(20)) {
            val aw = AgentWrap(agentId)
            connectAgent(aw) { ui, agent ->
                ui.getAgent()?.agentStatus shouldBe AgentStatus.NOT_REGISTERED
                ui.getBuild()?.buildStatus shouldBe BuildStatus.ONLINE
                register(agentId) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERING
                ui.getBuild()?.buildStatus shouldBe BuildStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERED
                ui.getBuild()?.apply {
                    buildStatus shouldBe BuildStatus.ONLINE
                    instanceIds.size shouldBe 1
                }
            }.reconnect(aw) { ui, _ ->
                ui.getBuild()?.apply {
                    buildStatus shouldBe BuildStatus.ONLINE
                    instanceIds.size shouldBe 1
                }
            }
        }
    }

}
