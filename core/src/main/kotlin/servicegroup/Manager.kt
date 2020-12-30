package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.store.*
import io.ktor.application.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*


internal class ServiceGroupManager(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val commonStore by instance<CommonStore>()
    private val app by instance<Application>()

    private val _state = atomic(persistentHashMapOf<String, ServiceGroupDto>())

    init {
        runBlocking {
            val groups = commonStore.client.getAll<ServiceGroup>()
            _state.update {
                it.mutate { map ->
                    for (group in groups) {
                        map[group.id] = group.toDto()
                    }
                }
            }
        }
    }

    fun all(): Collection<ServiceGroupDto> = _state.value.values

    operator fun get(groupId: String): ServiceGroupDto? = _state.value[groupId]

    suspend fun syncOnAttach(groupId: String) {
        val oldGroups = _state.getAndUpdate { groups ->
            groups.takeIf { groupId in it } ?: groups.put(
                key = groupId,
                value = ServiceGroupDto(
                    id = groupId,
                    name = groupId,
                    systemSettings = SystemSettingsDto(
                        packages = app.drillDefaultPackages
                    )

                )
            )
        }
        val groups = _state.value
        if (groups !== oldGroups) {
            groups[groupId]?.let { commonStore.storeGroup(it.toModel()) }
        }
    }

    suspend fun update(group: ServiceGroupDto): ServiceGroupDto? = group.id.let { id ->
        val oldGroups = _state.getAndUpdate { groups ->
            groups[id]?.takeIf { it != group }?.let { groups.put(id, group) } ?: groups
        }
        val groups = _state.value
        groups[id]?.also {
            if (oldGroups !== groups) {
                oldGroups[id]?.also { commonStore.store(it, group) }
            }
        }
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

    suspend fun updateSystemSettings(
        serviceGroup: ServiceGroupDto,
        systemSettings: SystemSettingsDto
    ): ServiceGroupDto? =  update(
        serviceGroup.copy(
            systemSettings = SystemSettingsDto(
                packages = systemSettings.packages,
                sessionIdHeaderName = systemSettings.sessionIdHeaderName,
                targetHost = systemSettings.targetHost
            )
        )
    )

    private suspend fun CommonStore.store(
        oldValue: ServiceGroupDto,
        groupDto: ServiceGroupDto
    ): ServiceGroupDto = run {
        logger.debug { "Updating group ${groupDto.id}, old: $oldValue new: $groupDto" }
        storeGroup(groupDto.toModel())
    }.toDto()
}

private suspend fun CommonStore.storeGroup(
    group: ServiceGroup
): ServiceGroup = client.store(group)
