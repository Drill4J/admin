package com.epam.drill.admin.e2e.plugin

import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.plugin.api.message.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.jupiter.api.*

class SingleConnectPluginTest : E2EPluginTest() {

    @RepeatedTest(3)
    fun `test e2e plugin API`() {
        createSimpleAppWithPlugin<PTestStream> {
            connectAgent<Build1>("myServiceGroup") { _, _ ->
                val expectedContent = StatusMessage.serializer() stringify StatusMessage(StatusCodes.OK, "act")
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
