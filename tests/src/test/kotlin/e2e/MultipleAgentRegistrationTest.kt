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
import kotlin.test.Test

class MultipleAgentRegistrationTest : E2ETest() {

    private val agentIdPrefix = "parallelRegister"

    @Test
    fun `4 Agents should be registered in parallel`() {
        createSimpleAppWithUIConnection(delayBeforeClearData = 1_000) {
            repeat(4) {
                connectAgent(AgentWrap("$agentIdPrefix$it", "0.1.$it")) { ui, agent ->
                    ui.getAgent()?.agentStatus shouldBe AgentStatus.NOT_REGISTERED
                    ui.getBuild()?.buildStatus shouldBe BuildStatus.ONLINE
                    register("$agentIdPrefix$it") { status, _ ->
                        status shouldBe HttpStatusCode.OK
                    }
                    ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERING
                    //TODO floating status
                    ui.getBuild()//?.buildStatus shouldBe BuildStatus.BUSY
                    agent.`get-set-packages-prefixes`()
                    agent.`get-load-classes-datas`()
                    ui.getBuild()//?.buildStatus shouldBe BuildStatus.ONLINE
                    ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERED
                }
            }
        }
    }

}
