package com.epam.drill.admin.e2e

import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*
import io.ktor.util.*
import kotlin.test.*

class PluginUnloadTest : E2ETest() {

    private val agentId = "pluginUnload"

    @OptIn(KtorExperimentalAPI::class)
    @Ignore("Disabled cuzz can't unload now!")
    @Test
    fun `Plugin unload test`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentId)) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(agentId) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE

                addPlugin(agentId, testPlugin)

                agent.getLoadedPlugin { metadata, file ->
                    hex(sha1(file)) shouldBe metadata.checkSum
                    ui.getAgent()?.status shouldBe AgentStatus.BUSY

                }

                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                unLoadPlugin("pluginUnload1", testPlugin)
                ui.getAgent()?.plugins?.count() shouldBe 1
            }
        }
    }

}
