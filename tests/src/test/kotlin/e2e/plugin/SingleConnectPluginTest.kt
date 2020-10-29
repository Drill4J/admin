package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.endpoints.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.plugin.api.end.*
import io.kotlintest.*
import io.ktor.http.*
import kotlin.test.*

class SingleConnectPluginTest : E2EPluginTest() {

    @Test
    fun `test e2e plugin API`() {
        createSimpleAppWithPlugin<PTestStream> {
            connectAgent<Build1>("myServiceGroup") { _, _ ->
                val expectedContent = StatusResponse.serializer() stringify ActionResult(
                    code = HttpStatusCode.OK.value,
                    data = "act"
                ).statusResponse()
                pluginAction("x") { status, content ->
                    status shouldBe HttpStatusCode.OK
                    content shouldBe expectedContent
                }.join()
                println("hi ag1")
            }.reconnect<Build2> { _, _ ->
                println("hi reconnected ag1")
            }
        }

    }


}
