package com.epam.drill.admin.agent.logging

import com.epam.drill.admin.api.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.api.*
import com.epam.drill.api.dto.*
import com.epam.kodux.*
import org.kodein.di.*
import org.kodein.di.generic.*

class LoggingHandler(override val kodein: Kodein) : KodeinAware {
    private val store by instance<StoreManager>()
    private val agentManager by instance<AgentManager>()

    suspend fun updateConfig(agentId: String, loggingConfig: LoggingConfigDto) {
        agentManager.agentSession(agentId)?.apply {
            sendConfig(loggingConfig)
            val agentStore = store.agentStore(agentId)
            agentStore.store(AgentLoggingConfig(agentId, loggingConfig))
        }
    }

    suspend fun sync(agentId: String, agentSession: AgentWsSession?) {
        store.getConfig(agentId)?.apply {
            agentSession?.sendConfig(config)
        }
    }

    private suspend fun StoreManager.getConfig(agentId: String) = agentStore(agentId).run {
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
