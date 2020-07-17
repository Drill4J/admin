package com.epam.drill.admin.agent

import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*
import org.kodein.di.*
import org.kodein.di.generic.*

@Serializable
data class AgentHost(
    @Id val agentId: String,
    val host: String
)

class HostManager(override val kodein: Kodein) : KodeinAware {
    private val store by instance<StoreManager>()

    operator fun get(agentId: String) = _hosts.value[agentId]

    private val _hosts = atomic(persistentMapOf<String, String>())

    suspend fun update(agentId: String, host: String) {
        _hosts.update { it.put(agentId, host) }.also {
            store.agentStore(agentId).store(AgentHost(agentId, host))
        }
    }

    suspend fun sync(agentId: String) {
        store.getHost(agentId)?.apply {
            _hosts.update { it.put(agentId, this.host) }
        }
    }

    private suspend fun StoreManager.getHost(agentId: String) = agentStore(agentId).run {
        findById<AgentHost>(agentId)
    }
}
