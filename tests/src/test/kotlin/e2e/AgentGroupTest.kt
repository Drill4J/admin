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
import com.epam.drill.admin.api.group.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.channels.*
import kotlin.test.*


class AgentGroupTest : E2ETest() {

    @Test
    fun `emulate microservices registration`() {
        val wit = 0
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap("ag$wit", "0.1.$wit", "micro")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(
                    "ag$wit",
                    payload = AgentRegistrationDto(
                        name = "first first",
                        description = "ad",
                        systemSettings = SystemSettingsDto(
                            packages = listOf("testPrefix")
                        ),
                        plugins = emptyList()
                    )
                ) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }
            val it = 1
            connectAgent(AgentWrap("ag$it", "0.1.$it", "micro")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(
                    "ag$it",
                    payload = AgentRegistrationDto(
                        name = "first first",
                        description = "ad",
                        systemSettings = SystemSettingsDto(
                            packages = listOf("testPrefix")
                        ),
                        plugins = emptyList()
                    )
                ) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
            }


            uiWatcher { x ->

                println(receiveAgents(x))
                println(receiveAgents(x))
                println(receiveAgents(x))
                println(receiveAgents(x))

                register("micro") { status, _ ->
                    status shouldBe HttpStatusCode.BadRequest
                }.join()

            }
        }
    }

    private suspend fun receiveAgents(uiChannel: Channel<GroupedAgentsDto>) =
        uiChannel.receive().grouped.flatMap { it.agents }.map { it.id to it.status to it.environment }

}
