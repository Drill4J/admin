/**
 * Copyright 2020 - 2022 EPAM Systems
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
package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*


abstract class PluginTest : PostgresBased("plugin") {

    val agentId = "ag"
    val buildVersion = "0.1.0"
    private val agentInfo = AgentInfo(
        id = agentId,
        name = agentId,
        description = "",
        buildVersion = buildVersion,
        agentType = "JAVA",
        agentVersion = "0.1.0"
    )
    val agentKey = AgentKey(agentId, buildVersion)

    private val sender = EmptySender

    private val emptyAdminData = object : AdminData {
    }

    protected suspend fun initPlugin(
        buildVersion: String,
        adminData : AdminData = emptyAdminData,
    ): Plugin = Plugin(
        adminData,
        sender,
        storeClient,
        agentInfo.copy(buildVersion = buildVersion),
        "test2code"
    ).apply {
        initialize()
        processData("smth_$buildVersion", Initialized(""))
        return this
    }

}

private object EmptySender : Sender {
    override suspend fun send(context: SendContext, destination: Any, message: Any) {}
    override suspend fun sendAgentAction(agentId: String, pluginId: String, message: Any) {}

}
