package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*
import org.apache.commons.codec.digest.*
import java.io.*

class BuildsTest : E2ETest() {

    @org.junit.jupiter.api.Test
    fun `can add new builds and rename aliases`() {
        createSimpleAppWithUIConnection(agentStreamDebug = false, uiStreamDebug = false) {
            val aw = AgentWrap("ag1")
            connectAgent(aw) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                register(aw.id).first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`("DrillExtension1.class")
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 1

            }.reconnect(aw.copy(buildVersion = "0.1.2")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`("DrillExtension2.class")
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 2
                renameBuildVersion(aw.id, payload = AgentBuildVersionJson("0.1.2", "wtf"))
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 2
            }.reconnect(aw.copy(buildVersion = "0.1.3")) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()?.size shouldBe 3
                renameBuildVersion(aw.id, payload = AgentBuildVersionJson("0.1.3", "omg"))
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                ui.getBuilds()
                val builds = ui.getBuilds()
                builds!!.size shouldBe 3
                builds.find { it.buildVersion == "0.1.0" }!!.alias shouldBe "sad"
                builds.find { it.buildVersion == "0.1.2" }!!.alias shouldBe "wtf"
                builds.find { it.buildVersion == "0.1.3" }!!.alias shouldBe "omg"
            }
        }

    }

}

