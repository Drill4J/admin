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

class LoggingHandler(override val kodein: Kodein) : KodeinAware {
    private val stores by instance<AgentStores>()
    private val agentManager by instance<AgentManager>()

    suspend fun updateConfig(agentId: String, loggingConfig: LoggingConfigDto) {
        agentManager.agentSessions(agentId).applyEach {
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
    sendToTopic<Communication.Agent.UpdateLoggingConfigEvent>(loggingConfig.level.toConfig())
}

private fun LogLevel.toConfig(): LoggingConfig = LoggingConfig(
    trace = this == LogLevel.TRACE,
    debug = this <= LogLevel.DEBUG,
    info = this <= LogLevel.INFO,
    warn = this <= LogLevel.WARN,
    error = true
)
