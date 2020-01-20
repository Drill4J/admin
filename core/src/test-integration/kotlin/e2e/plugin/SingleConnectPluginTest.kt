package com.epam.drill.admin.e2e.plugin

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
                pluginAction("x") { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()
                println("hi ag1")
            }.reconnect<Build2> { _, _ ->
                println("hi reconnected ag1")
            }
        }

    }


}
