package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*
import org.apache.commons.codec.digest.*
import org.junit.*

class BuildsTest : AbstractE2ETest() {

    @Test(timeout = 10000)
    fun `can add new builds and rename aliases`() {
        createSimpleAppWithUIConnection(agentStreamDebug = false, uiStreamDebug = false) {
            val aw = AgentWrap("ag1")
            connectAgent(aw) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                register(aw.id).first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-data`("DrillExtension1.class")
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                addPlugin(aw.id, pluginT2CM)

                agent.getLoadedPlugin { metadata, file ->
                    DigestUtils.md5Hex(file) shouldBe metadata.md5Hash
                    ui.getAgent()?.status shouldBe AgentStatus.BUSY

                }
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 1

            }.reconnect(aw.copy(buildVersion = "0.1.2")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-data`("DrillExtension2.class")
                agent.getLoadedPlugin { metadata, file ->
                    DigestUtils.md5Hex(file) shouldBe metadata.md5Hash
                }
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 2
                renameBuildVersion(aw.id, payload = AgentBuildVersionJson("0.1.2", "wtf"))
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 2
            }.reconnect(aw.copy(buildVersion = "0.1.3")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-data`()
                agent.getLoadedPlugin { metadata, file ->
                    DigestUtils.md5Hex(file) shouldBe metadata.md5Hash
                }
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 3
                renameBuildVersion(aw.id, payload = AgentBuildVersionJson("0.1.3", "omg"))
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                val builds = ui.getBuilds()
                builds?.size shouldBe 3
            }
        }

    }

}

