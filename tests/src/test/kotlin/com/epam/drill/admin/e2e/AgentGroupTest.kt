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
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.waitUntil
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.jupiter.api.*
import kotlin.time.*


class AgentGroupTest : E2ETest() {

    @Test
    fun `emulate microservices registration`() {
        val wit = 0
        createSimpleAppWithUIConnection(timeout = Duration.seconds(20)) {
            connectAgent(AgentWrap("ag$wit", "0.1.$wit", "micro")) { _, ui, agent ->
                waitUntil { ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERED }
                waitUntil { ui.getBuild()?.buildStatus shouldBe BuildStatus.ONLINE }
            }
            val it = 1
            connectAgent(AgentWrap("ag$it", "0.1.$it", "micro")) { _, ui, agent ->
                waitUntil { ui.getAgent()?.agentStatus shouldBe AgentStatus.REGISTERED }
                waitUntil { ui.getBuild()?.buildStatus shouldBe BuildStatus.ONLINE }
            }


            uiWatcher { glob ->
                println(glob.getGroupedAgents().groupAgents())
                println(glob.getGroupedAgents().groupAgents())
                println(glob.getGroupedAgents().groupAgents())
                println(glob.getGroupedAgents().groupAgents())

                register("micro") { status, _ ->
                    status shouldBe HttpStatusCode.BadRequest
                }.join()

            }
        }
    }

    private fun GroupedAgentsDto.groupAgents(
    ) = grouped.flatMap { it.agents }.map { it.id to it.agentStatus to it.environment }

}
