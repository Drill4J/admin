package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*
import org.apache.commons.codec.digest.*
import org.junit.jupiter.api.*

class PluginUnloadTest : AbstractE2ETest() {

    @Disabled("Disabled cuzz can't unload now!")
    @org.junit.jupiter.api.Test
    fun `Plugin unload test`() {
        createSimpleAppWithUIConnection {
            connectAgent(AgentWrap("ag1")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                register("ag1").first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE

                addPlugin("ag1", pluginT2CM)

                agent.getLoadedPlugin { metadata, file ->
                    DigestUtils.md5Hex(file) shouldBe metadata.md5Hash
                    ui.getAgent()?.status shouldBe AgentStatus.BUSY

                }

                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                unLoadPlugin("ag1", pluginT2CM)
                ui.getAgent()?.pluginsCount shouldBe 1
            }
        }
    }

}