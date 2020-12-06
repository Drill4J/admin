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
        _state.update { groups ->
            when (groups[groupId]) {
                null -> groups.put(groupId, commonStore.ensureGroup(groupId))
                else -> groups
            }
        }
    }

    suspend fun update(group: ServiceGroupDto): ServiceGroupDto? {
        val (id) = group
        return _state.updateAndGet { groups ->
            when (val oldValue: ServiceGroupDto? = groups[id] ?: commonStore.findDtoById(id)) {
                null -> groups
                group -> groups.put(id, group)
                else -> groups.put(id, commonStore.store(oldValue, group))
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

    private suspend fun CommonStore.ensureGroup(groupId: String): ServiceGroupDto = run {
        findDtoById(groupId) ?: create(groupId)
    }

    private suspend fun create(groupId: String): ServiceGroupDto = run {
        logger.debug { "Creating group '$groupId'..." }
        commonStore.storeGroup(
            ServiceGroup(
                id = groupId,
                name = groupId,
                systemSettings = SystemSettingsDto(
                    packages = app.drillDefaultPackages
                )
            )
        )
    }.toDto()

    private suspend fun CommonStore.store(
        oldValue: ServiceGroupDto,
        groupDto: ServiceGroupDto
    ): ServiceGroupDto = run {
        logger.debug { "Updating group ${groupDto.id}, old: $oldValue new: $groupDto" }
        storeGroup(groupDto.toModel())
    }.toDto()
}

private suspend fun CommonStore.findDtoById(
    groupId: String
): ServiceGroupDto? = client.findById<ServiceGroup>(groupId)?.toDto()

private suspend fun CommonStore.storeGroup(
    group: ServiceGroup
): ServiceGroup = client.store(group)
