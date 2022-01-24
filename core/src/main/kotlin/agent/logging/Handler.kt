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
package com.epam.drill.admin.agent.logging

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.store.*
import com.epam.drill.api.*
import com.epam.drill.api.dto.*
import com.epam.kodux.*
import org.kodein.di.*
import org.kodein.di.generic.*

//todo remove after testing EPMDJ-7890
class LoggingHandler(override val kodein: Kodein) : KodeinAware {
    private val stores by instance<AgentStores>()
    private val buildManager by instance<BuildManager>()

    suspend fun updateConfig(agentId: String, loggingConfig: LoggingConfigDto) {
        buildManager.agentSessions(agentId).applyEach {
            sendConfig(loggingConfig)
            stores[agentId].store(AgentLoggingConfig(agentId, loggingConfig))
        }
    }

    suspend fun sync(agentId: String, agentSession: AgentWsSession?) {
        stores[agentId].loadConfig(agentId)?.apply {
            agentSession?.sendConfig(config)
        }
    }

    private suspend fun StoreClient.loadConfig(agentId: String) = run {
        findById<AgentLoggingConfig>(agentId)
    }
}

suspend fun AgentWsSession.sendConfig(loggingConfig: LoggingConfigDto) {
    sendToTopic<Communication.Agent.UpdateLoggingConfigEvent, LoggingConfig>(loggingConfig.level.toConfig())
}

private fun LogLevel.toConfig(): LoggingConfig = LoggingConfig(
    trace = this == LogLevel.TRACE,
    debug = this <= LogLevel.DEBUG,
    info = this <= LogLevel.INFO,
    warn = this <= LogLevel.WARN,
    error = true
)
