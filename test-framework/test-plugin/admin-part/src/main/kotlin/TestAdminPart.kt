/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.plugins.coverage


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.kodux.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
class TestAdminPart(
    adminData: AdminData,
    sender: Sender,
    @Suppress("UNUSED_PARAMETER") storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String
) : AdminPluginPart<String>(
    id = id,
    agentInfo = agentInfo,
    adminData = adminData,
    sender = sender
) {

    var packagesChangesCount = 0

    val sendContext = AgentSendContext(agentInfo.id, agentInfo.buildVersion)

    override suspend fun processData(instanceId: String, content: String): Any {
        sender.send(sendContext, "/processed-data", listOf("xx"))
        return ""
    }

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

    override fun parseAction(rawAction: String): String = rawAction

    override suspend fun applyPackagesChanges() {
        packagesChangesCount++
        sender.send(sendContext, "/packagesChangesCount", packagesChangesCount)
    }
}
