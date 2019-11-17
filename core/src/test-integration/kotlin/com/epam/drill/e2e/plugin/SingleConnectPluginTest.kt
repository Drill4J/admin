package com.epam.drill.e2e.plugin

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.jupiter.api.*

class SingleConnectPluginTest : E2EPluginTest() {

    @RepeatedTest(3)
    fun testE2ePluginAPI() {
        createSimpleAppWithPlugin<PTestStream> {
            connectAgent<Build1>("myServiceGroup") { _, _ ->
                pluginAction("x").first shouldBe HttpStatusCode.OK
                println("hi ag1")
            }.reconnect<Build2> { _, _ ->
                println("hi reconnected ag1")
            }
        }

    }


}