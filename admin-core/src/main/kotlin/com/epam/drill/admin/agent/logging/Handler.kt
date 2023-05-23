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
package com.epam.drill.admin.agent.logging

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.store.*
import com.epam.drill.api.*
import com.epam.drill.api.dto.*
import com.epam.dsm.*
import org.kodein.di.*

//todo remove after testing EPMDJ-7890
class LoggingHandler(override val di: DI) : DIAware {
    private val buildManager by instance<BuildManager>()

    suspend fun updateConfig(agentId: String, loggingConfig: LoggingConfigDto) {
        buildManager.agentSessions(agentId).applyEach {
            sendConfig(loggingConfig)
            adminStore.store(AgentLoggingConfig(agentId, loggingConfig))
        }
    }

    /**
     * Send the information about logging to the agent
     * @param agentId the agent ID
     * @param agentSession the agent WebSocket session
     * @features Agent attaching
     */
    suspend fun sync(agentId: String, agentSession: AgentWsSession?) {
        adminStore.loadConfig(agentId)?.apply {
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
