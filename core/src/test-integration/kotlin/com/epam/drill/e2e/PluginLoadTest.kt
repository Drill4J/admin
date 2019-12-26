package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.apache.commons.codec.digest.*
import kotlin.test.*


class PluginLoadTest : E2ETest() {

    private val agentId = "pluginLoad"

    @Test
    fun `Plugin Load Test`() {
        createSimpleAppWithUIConnection(true, true) {
            connectAgent(AgentWrap(agentId)) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                launch {
                    register(agentId).first shouldBe HttpStatusCode.OK
                }
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE
                GlobalScope.launch {
                    //add plugin blocks thread now.. will act it in background coroutine
                    addPlugin(agentId, testPlugin).first shouldBe HttpStatusCode.OK
                }

                agent.getLoadedPlugin { metadata, file ->
                    DigestUtils.md5Hex(file) shouldBe metadata.md5Hash

                    ui.getAgent()?.status shouldBe AgentStatus.BUSY

                }
                ui.getAgent()?.apply {
                    status shouldBe AgentStatus.ONLINE
                    activePluginsCount shouldBe 1
                }
            }
        }
    }
}