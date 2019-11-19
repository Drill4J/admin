package com.epam.drill.e2e.plugin

import com.epam.drill.agentmanager.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.channels.*
import org.junit.jupiter.api.*

class ApplyPackagesChangesTriggerTest : E2EPluginTest() {

    @Test
    fun `Apply Packages Changes Trigger Test`() {
        createSimpleAppWithPlugin<PTestStream> (){
            connectAgent<Build1>("packagesPrefixes") { _, _ ->
                pluginAction("packagesChangesCount").second shouldBe "0"
            }.reconnect<Build1> { _, _ ->
                pluginAction("packagesChangesCount").second shouldBe "0"
            }.reconnect<Build2> { ui, _ ->
                pluginAction("packagesChangesCount").second shouldBe "0"
                changePackages(
                    agentId = agentId,
                    payload = PackagesPrefixes(listOf("testPrefix"))
                ).first shouldBe HttpStatusCode.OK
                ui.packagesChangesCount.receive()
                ui.packagesChangesCount.receive() shouldBe "1"

            }
        }

    }

}