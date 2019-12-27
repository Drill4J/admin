package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.store.*
import com.epam.drill.common.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.router.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*


class ServiceGroupManager(override val kodein: Kodein) : KodeinAware {

    private val logger = KotlinLogging.logger {}

    private val commonStore: CommonStore by instance()
    private val topicResolver: TopicResolver by instance()

    private val _state = atomic(persistentHashMapOf<String, ServiceGroup>())

    operator fun get(groupId: String) = _state.value[groupId]

    suspend fun syncOnAttach(groupId: String) {
        _state.update { groups ->
            when (groups[groupId]) {
                null -> groups.put(groupId, commonStore.ensureGroup(groupId))
                else -> groups
            }
        }
    }

    suspend fun update(group: ServiceGroup): ServiceGroup? {
        val (id) = group
        return _state.updateAndGet { groups ->
            when (val oldValue: ServiceGroup? = groups[id] ?: commonStore.client.findById(id)) {
                null -> groups
                group -> groups.put(id, group)
                else -> groups.put(id, store(oldValue, group))
            }
        }[id]
    }

    fun group(agents: Iterable<AgentInfo>): GroupedAgents {
        val groups = _state.value
        val agentGroups = agents.filter { it.serviceGroup.isEmpty() || it.serviceGroup in groups }
            .groupBy { it.serviceGroup }
        val singleAgents = SingleAgents(agentGroups[""] ?: emptyList())
        val groupedAgents = agentGroups.filterKeys(String::isNotEmpty)
            .map { (key, value) -> AgentGroup(groups[key]!!, value) }
        return singleAgents to groupedAgents
    }

    private suspend fun CommonStore.ensureGroup(groupId: String): ServiceGroup =
        client.findById(groupId) ?: create(groupId)

    private suspend fun create(groupId: String): ServiceGroup {
        logger.debug { "Creating group $groupId" }
        return commonStore.client.store(ServiceGroup(id = groupId, name = groupId))
    }

    private suspend fun store(oldValue: ServiceGroup, group: ServiceGroup): ServiceGroup {
        logger.debug { "Updating group ${group.id}, old: $oldValue new: $group" }
        commonStore.client.store(group)
        topicResolver.sendToAllSubscribed(WsRoutes.ServiceGroup(groupId = group.id))
        return group
    }
}
