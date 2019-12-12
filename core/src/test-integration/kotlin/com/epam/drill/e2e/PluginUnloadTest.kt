package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*
import org.apache.commons.codec.digest.*
import org.junit.jupiter.api.*

class PluginUnloadTest : E2ETest() {

    private val agentId = "pluginUnload"

    @Disabled("Disabled cuzz can't unload now!")
    @org.junit.jupiter.api.Test
    fun `Plugin unload test`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap(agentId)) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                register(agentId).first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE

                addPlugin(agentId, testPlugin)

                agent.getLoadedPlugin { metadata, file ->
                    DigestUtils.md5Hex(file) shouldBe metadata.md5Hash
                    ui.getAgent()?.status shouldBe AgentStatus.BUSY

                }

                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                unLoadPlugin("pluginUnload1", testPlugin)
                ui.getAgent()?.plugins?.count() shouldBe 1
            }
        }
    }

}