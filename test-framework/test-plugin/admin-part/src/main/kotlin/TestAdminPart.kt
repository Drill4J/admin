package com.epam.drill.admin.plugins.coverage


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.kodux.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.*

@Serializable
data class Stuff(val s: String)

@Suppress("unused", "MemberVisibilityCanBePrivate")
class TestAdminPart(
    adminData: AdminData,
    sender: Sender,
    storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String
) : AdminPluginPart<String>(adminData, sender, storeClient, agentInfo, id) {

    var packagesChangesCount = 0

    val sendContext = AgentSendContext(agentInfo.id, agentInfo.buildVersion)

    override suspend fun processData(dm: DrillMessage): Any {
        sender.send(sendContext, "/processed-data", listOf("xx"))
        return ""
    }

    override suspend fun dropData() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val serDe: SerDe<String> = SerDe(String.serializer())

    override suspend fun doAction(action: String): Any {
        return when (action) {
            "packagesChangesCount" -> {
                println(packagesChangesCount)
                ActionResult(200, packagesChangesCount.toString())
            }
            else -> {
                println(action)
                ActionResult(200, "act")
            }
        }
    }

    override suspend fun applyPackagesChanges() {
        packagesChangesCount++
        sender.send(sendContext, "/packagesChangesCount", packagesChangesCount)
    }
}
