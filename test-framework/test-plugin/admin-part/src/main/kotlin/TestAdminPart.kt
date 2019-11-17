package com.epam.drill.plugins.coverage


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.kodux.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.modules.*


@Suppress("unused", "MemberVisibilityCanBePrivate")
class TestAdminPart(
    adminData: AdminData,
    sender: Sender,
    storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String
) : AdminPluginPart<String>(adminData, sender, storeClient, agentInfo, id) {
    override suspend fun processData(dm: DrillMessage): Any {
        sender.send(agentInfo.id, agentInfo.buildVersion, "new-destination", dm)
        return ""
    }

    override suspend fun dropData() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val serDe: SerDe<String> = SerDe(StringSerializer)
    override suspend fun doAction(action: String): Any {
        println(action)
        return "act"
    }
}