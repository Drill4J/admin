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
package com.epam.drill.admin.endpoints

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.logging.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.group.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.util.*
import com.epam.drill.api.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import kotlin.time.*


class AgentManager(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    internal val agentStorage by instance<AgentStorage>()
    internal val plugins by instance<Plugins>()

    internal val activeAgents: List<AgentInfo>
        get() = agentStorage.values
            .map { it.agent }
            .filter { instanceIds(it.id).isNotEmpty() }
            .sortedWith(compareBy(AgentInfo::id))

    private val app by instance<Application>()
    private val topicResolver by instance<TopicResolver>()
    private val commonStore by instance<CommonStore>()
    private val agentStores by instance<AgentStores>()
    private val pluginSenders by instance<PluginSenders>()
    private val groupManager by instance<GroupManager>()
    private val agentDataCache by instance<AgentDataCache>()
    private val notificationsManager by instance<NotificationManager>()
    private val loggingHandler by instance<LoggingHandler>()

    private val _ignoredInstances = atomic(
        persistentHashMapOf<String, PersistentList<String>>()
    )

    init {
        trackTime("loadingAgents") {
            runBlocking {
                val store = commonStore.client
                val registered = store.getAll<AgentInfo>()
                val prepared = store.getAll<PreparedAgentData>().map { data ->
                    agentDataCache[data.id] = AgentData(data.id, agentStores, data.dto.systemSettings)
                    data.dto.toAgentInfo(plugins)
                }
                val registeredMap = registered.associate {
                    adminData(it.id).initBuild(it.buildVersion)
                    it.id to AgentEntry(it)
                }
                val preparedMap = prepared.filter { it.id !in registeredMap }.associate {
                    it.id to AgentEntry(it)
                }
                (registeredMap + preparedMap).takeIf { it.any() }?.let { entryMap ->
                    agentStorage.init(entryMap)
                }
            }
        }
    }

    internal suspend fun prepare(
        dto: AgentCreationDto,
    ): AgentInfo? = (get(dto.id) ?: loadAgentInfo(dto.id)).let { storedInfo ->
        if (storedInfo == null || storedInfo.agentVersion.none() || !storedInfo.isRegistered) {
            logger.debug { "Preparing agent ${dto.id}..." }
            dto.toAgentInfo(plugins).also { info: AgentInfo ->
                agentDataCache[dto.id] = AgentData(dto.id, agentStores, dto.systemSettings)
                commonStore.client.store(
                    PreparedAgentData(id = dto.id, dto = dto)
                )
                commonStore.client.deleteById<AgentInfo>(dto.id)
                agentStorage.put(dto.id, AgentEntry(info))
                logger.debug { "Prepared agent ${dto.id}." }
            }
        } else null
    }

    internal suspend fun attach(
        config: CommonAgentConfig,
        needSync: Boolean,
        session: AgentWsSession,
    ): AgentInfo {
        logger.debug { "Attaching agent: needSync=$needSync, config=$config" }
        val id = config.id
        val groupId = config.serviceGroupId
        logger.debug { "Group id '$groupId'" }
        if (groupId.isNotBlank()) {
            groupManager.syncOnAttach(groupId)
        }
        val oldInstanceIds = instanceIds(id)
        val buildVersion = config.buildVersion
        val existingEntry = agentStorage[id]
        val currentInfo = existingEntry?.agent
        val adminData = adminData(id)
        val isNewBuild = adminData.initBuild(buildVersion)
        loggingHandler.sync(id, session)
        //TODO agent instances
        return if (
            (oldInstanceIds.isEmpty() || currentInfo?.isRegistered == true) &&
            currentInfo?.buildVersion == buildVersion &&
            currentInfo.groupId == groupId &&
            currentInfo.agentVersion == config.agentVersion
        ) {
            logger.debug { "agent($id, $buildVersion): reattaching to current build..." }
            existingEntry.addInstanceId(config.instanceId, session)
            notifySingleAgent(id)
            notifyAllAgents()
            currentInfo.plugins.initPlugins(existingEntry)
            if (needSync) app.launch {
                currentInfo.sync(config.instanceId) // sync only existing info!
            }
            currentInfo.persistToDatabase()
            session.updateSessionHeader(adminData.settings.sessionIdHeaderName)
            currentInfo
        } else {
            logger.debug { "agent($id, $buildVersion, ${config.instanceId}): attaching to new or stored build..." }
            val storedInfo: AgentInfo? = loadAgentInfo(id)
            val preparedInfo: AgentInfo? = preparedInfo(storedInfo, id)
            val existingInfo = (storedInfo ?: preparedInfo)?.copy(
                buildVersion = config.buildVersion,
                agentVersion = config.agentVersion
            )
            val info: AgentInfo = existingInfo ?: config.toAgentInfo()
            val entry = AgentEntry(info)
            entry.addInstanceId(config.instanceId, session)
            agentStorage.put(id, entry)?.also { oldEntry ->
                oldEntry.close()
            }
            existingInfo?.plugins?.initPlugins(entry)
            app.launch {
                existingInfo?.takeIf { needSync }?.sync(config.instanceId, true) // sync only existing info!
                if (isNewBuild && currentInfo != null) {
                    notificationsManager.saveNewBuildNotification(info)
                }
            }
            info.persistToDatabase()
            preparedInfo?.let { commonStore.client.deleteById<PreparedAgentData>(it.id) }
            session.updateSessionHeader(adminData.settings.sessionIdHeaderName)
            info
        }
    }

    private suspend fun preparedInfo(
        storedInfo: AgentInfo?,
        id: String,
    ) = if (storedInfo == null) {
        commonStore.client.findById<PreparedAgentData>(id)?.let {
            agentDataCache[id] = AgentData(id, agentStores, it.dto.systemSettings)
            it.dto.toAgentInfo(plugins)
        }
    } else null

    private suspend fun Collection<String>.initPlugins(
        agentEntry: AgentEntry,
    ) {
        val agentInfo = agentEntry.agent
        val logPrefix by lazy(LazyThreadSafetyMode.NONE) {
            "Agent(id=${agentInfo.id}, buildVersion=${agentInfo.id}):"
        }
        forEach { pluginId ->
            plugins[pluginId]?.let { plugin ->
                ensurePluginInstance(agentEntry, plugin)
                logger.info { "$logPrefix initialized plugin $pluginId" }
            } ?: logger.error { "$logPrefix plugin $pluginId not loaded!" }
        }
    }

    internal fun setIgnoredInstances(
        ignoredInstances: List<InstanceDto>,
    ) = _ignoredInstances.updateAndGet {
        ignoredInstances.associate {
            it.agentId to it.instanceId.toPersistentList()
        }.toPersistentHashMap()
    }

    internal fun isInstanceIgnored(
        agentId: String, instanceId: String,
    ) = _ignoredInstances.value[agentId]?.contains(instanceId) ?: false

    suspend fun register(
        agentId: String,
        dto: AgentRegistrationDto,
    ) = entryOrNull(agentId)?.let { entry ->
        val pluginsToAdd = dto.plugins.mapNotNull(plugins::get)
        val info: AgentInfo = entry.updateAgent { info ->
            info.copy(
                name = dto.name,
                environment = dto.environment,
                description = dto.description,
                isRegistered = true,
                plugins = pluginsToAdd.mapTo(mutableSetOf()) { it.pluginBean.id }
            )
        }.apply { persistToDatabase() }
        adminData(agentId).updateSettings(dto.systemSettings)
        pluginsToAdd.forEach { plugin -> ensurePluginInstance(entry, plugin) }
        instanceIds(agentId).entries.mapIndexed { index, (instanceId, _) ->
            info.sync(instanceId, index == 0)
        }
    }

    internal suspend fun removeInstance(
        agentId: String,
        instanceId: String,
    ) {
        entryOrNull(agentId)?.let { entry ->
            val instances = entry.updateAgent {
                it.copy(instances = it.instances - instanceId)
            }.instances
            if (instances.isEmpty()) {
                logger.info { "Agent with id '${agentId}' was disconnected" }
                agentStorage.handleRemove(agentId)
                agentStorage.update()
            } else {
                notifySingleAgent(agentId)
                logger.info { "Instance '$instanceId' of Agent '${agentId}' was disconnected" }
            }
        }
    }

    internal fun instanceIds(
        agentId: String,
    ): PersistentMap<String, InstanceState> = getOrNull(agentId)?.let { agentInfo ->
        agentInfo.instances.also {
            logger.trace { "instances ids of agent(id=$agentId, version=${agentInfo.buildVersion}): ${it.keys}" }
        }
    } ?: persistentHashMapOf()

    internal fun ignoredInstances(agentId: String) = _ignoredInstances.value[agentId] ?: emptyList()

    internal suspend fun updateAgent(
        agentId: String,
        agentUpdateDto: AgentUpdateDto,
    ) = entryOrNull(agentId)?.also { entry ->
        entry.updateAgent {
            it.copy(
                name = agentUpdateDto.name,
                environment = agentUpdateDto.environment,
                description = agentUpdateDto.description
            )
        }.commitChanges()
        topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(agentId))
    }

    internal suspend fun resetAgent(agInfo: AgentInfo) = entryOrNull(agInfo.id)?.also { entry ->
        logger.debug { "Reset agent ${agInfo.id}" }
        entry.updateAgent {
            it.copy(
                name = "",
                environment = "",
                description = "",
                isRegistered = false
            )
        }.commitChanges()
        logger.debug { "Agent ${agInfo.id} has been reset" }
        topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(agInfo.id))
    }

    private suspend fun notifyAllAgents() {
        agentStorage.update()
    }

    private suspend fun notifySingleAgent(agentId: String) {
        agentStorage.singleUpdate(agentId)
    }

    fun agentSessions(k: String) = instanceIds(k).values.mapNotNull { state ->
        state.takeIf { it.status == AgentStatus.ONLINE }?.agentWsSession
    }

    fun buildVersionByAgentId(agentId: String) = getOrNull(agentId)?.buildVersion ?: ""

    operator fun contains(k: String) = k in agentStorage.targetMap

    internal fun getOrNull(agentId: String) = agentStorage.targetMap[agentId]?.agent

    internal operator fun get(agentId: String) = agentStorage.targetMap[agentId]?.agent

    internal fun entryOrNull(agentId: String): AgentEntry? = agentStorage.targetMap[agentId]

    internal fun allEntries(): Collection<AgentEntry> = agentStorage.targetMap.values

    suspend fun addPlugins(
        agentId: String,
        pluginIds: Set<String>,
    ) = entryOrNull(agentId)!!.let { entry ->
        val existingIds = entry.agent.plugins
        val pluginsToAdd = pluginIds.filter { it !in existingIds }.mapNotNull(plugins::get)
        if (pluginsToAdd.any()) {
            val updatedInfo = entry.updateAgent { agent ->
                val updatedPlugins = agent.plugins + pluginsToAdd.map { it.pluginBean.id }
                agent.copy(
                    plugins = updatedPlugins
                )
            }
            supervisorScope {
                instanceIds(agentId).forEach { (instanceId, _) ->
                    pluginsToAdd.forEach { plugin ->
                        val pluginId = plugin.pluginBean.id
                        wrapInstanceBusy(agentId, instanceId) {
                            launch {
                                ensurePluginInstance(entry, plugin)
                                sendPlugin(plugin, updatedInfo).await()
                                enablePlugin(pluginId, updatedInfo).await()
                            }
                        }
                    }
                    topicResolver.sendToAllSubscribed(WsRoutes.AgentPlugins(agentId))
                }
            }
        }
    }

    suspend fun addPlugins(
        agentInfos: Iterable<AgentInfo>,
        pluginIds: Set<String>,
    ): Set<String> = supervisorScope {
        agentInfos.map { info ->
            val agentId = info.id
            agentId to async { addPlugins(agentId, pluginIds) }
        }
    }.mapNotNullTo(mutableSetOf()) { (agentId, deferred) ->
        agentId.takeIf {
            runCatching { deferred.await() }.onFailure {
                logger.error(it) { "Error on adding plugins $pluginIds to agent $agentId" }
            }.isSuccess
        }
    }


    internal fun adminData(agentId: String): AgentData = agentDataCache.getOrPut(agentId) {
        logger.debug { "put adminData with id=$agentId" }
        AgentData(
            agentId,
            agentStores,
            SystemSettingsDto(packages = app.drillDefaultPackages)
        )
    }

    private suspend fun AgentWsSession.disableAllPlugins(agentId: String) {
        logger.debug { "Reset all plugins for agent with id $agentId" }
        getOrNull(agentId)?.plugins?.forEach { pluginId ->
            sendToTopic<Communication.Plugin.ToggleEvent, TogglePayload>(
                message = TogglePayload(pluginId, false)
            )
        }
        logger.debug { "All plugins for agent with id $agentId were disabled" }
    }

    private suspend fun AgentWsSession.enableAllPlugins(agentInfo: AgentInfo) {
        val agentDebugStr = agentInfo.debugString(instanceId)
        logger.debug { "Enabling all plugins for $agentDebugStr" }
        agentInfo.plugins.map { pluginId ->
            logger.debug { "Enabling plugin $pluginId for $agentDebugStr..." }
            enablePlugin(pluginId, agentInfo)
        }.forEach { it.await() }
        logger.debug { "All plugins for $agentDebugStr were enabled" }
    }

    private suspend fun AgentWsSession.enablePlugin(
        pluginId: String,
        agentInfo: AgentInfo,
    ): WsDeferred = sendToTopic<Communication.Plugin.ToggleEvent, TogglePayload>(
        message = TogglePayload(pluginId, true)
    ) {
        logger.debug { "Enabled plugin $pluginId for ${agentInfo.debugString(instanceId)}" }
    }

    private suspend fun loadAgentInfo(agentId: String): AgentInfo? = commonStore.client.findById(agentId)

    private suspend fun AgentInfo.persistToDatabase() {
        commonStore.client.store(this)
    }

    private suspend fun AgentWsSession.configurePackages(prefixes: List<String>) {
        setPackagesPrefixes(prefixes)
    }

    private suspend fun AgentInfo.sync(instanceId: String, needClassSending: Boolean = false) {
        val agentDebugStr = debugString(instanceId)
        if (isRegistered) {
            logger.debug { "$agentDebugStr: starting sync for instance $instanceId..." }
            val info = this
            val duration = measureTime {
                wrapInstanceBusy(id, instanceId) {
                    val settings = adminData(id).settings
                    configurePackages(settings.packages)
                    sendPlugins(info)
                    if (needClassSending) {
                        updateSessionHeader(settings.sessionIdHeaderName)
                        triggerClassesSending()
                        enableAllPlugins(info)
                    }
                }
            }
            logger.info { "$agentDebugStr: sync took: $duration." }
            topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(id))
            logger.debug { "$agentDebugStr: sync finished." }
        } else logger.warn { "$agentDebugStr: cannot sync, status is ${AgentStatus.NOT_REGISTERED}." }
    }

    private suspend fun AgentWsSession.updateSessionHeader(sessionIdHeaderName: String) {
        sendToTopic<Communication.Agent.ChangeHeaderNameEvent, String>(sessionIdHeaderName.toLowerCase())
    }

    suspend fun updateSystemSettings(agentId: String, settings: SystemSettingsDto) {
        val adminData = adminData(agentId)
        getOrNull(agentId)?.let { info ->
            adminData.updateSettings(settings) { oldSettings ->
                instanceIds(agentId).forEach { (instanceId, _) ->
                    wrapInstanceBusy(agentId, instanceId) {
                        if (oldSettings.sessionIdHeaderName != settings.sessionIdHeaderName) {
                            updateSessionHeader(settings.sessionIdHeaderName)
                        }
                        if (oldSettings.packages != settings.packages) {
                            disableAllPlugins(agentId)
                            configurePackages(settings.packages)
                            triggerClassesSending()
                            entryOrNull(agentId)?.applyPackagesChanges()
                            enableAllPlugins(info)
                        }
                    }
                }
            }
        }
    }


    internal suspend fun updateSystemSettings(
        agentInfos: Iterable<AgentInfo>,
        systemSettings: SystemSettingsDto,
    ): Set<String> = supervisorScope {
        agentInfos.map { info ->
            val agentId = info.id
            val handler = CoroutineExceptionHandler { _, e ->
                logger.error(e) { "Error updating agent $agentId" }
            }
            async(handler) {
                updateSystemSettings(agentId, systemSettings.copy(targetHost = adminData(agentId).settings.targetHost))
                agentId
            }
        }
    }.filterNot { it.isCancelled }.mapTo(mutableSetOf()) { it.await() }

    private suspend fun AgentWsSession.sendPlugins(info: AgentInfo) {
        val debugStr = info.debugString(instanceId)
        logger.debug { "Sending ${info.plugins.count()} plugins to $debugStr" }
        info.plugins.mapNotNull(plugins::get).map { pb ->
            sendPlugin(pb, info)
        }.forEach { it.await() }
        logger.debug { "Sent plugins ${info.plugins} to $debugStr" }
    }

    private suspend fun AgentWsSession.sendPlugin(
        plugin: Plugin,
        agentInfo: AgentInfo,
    ): WsDeferred {
        val pb = plugin.pluginBean
        val debugStr = agentInfo.debugString(instanceId)
        logger.debug { "Sending plugin ${pb.id} to $debugStr" }
        val data = if (agentInfo.agentType == AgentType.JAVA) {
            plugin.agentPluginPart.readBytes()
        } else byteArrayOf()
        pb.checkSum = hex(sha1(data))
        return async(
            topicName = "/agent/plugin/${pb.id}/loaded",
            callback = { logger.debug { "Sent plugin ${pb.id} to $debugStr" } }
        ) { //TODO move to the api
            sendToTopic<Communication.Agent.PluginLoadEvent, com.epam.drill.common.PluginBinary>(
                com.epam.drill.common.PluginBinary(pb, data)
            ).await()
            logger.debug { "Sent data of plugin ${pb.id} to $debugStr" }
        }
    }

    internal fun agentsByGroup(groupId: String): List<AgentEntry> = allEntries().filter {
        it.agent.groupId == groupId
    }

    private suspend fun ensurePluginInstance(
        agentEntry: AgentEntry,
        plugin: Plugin,
    ): AdminPluginPart<*> = plugin.pluginBean.id.let { pluginId ->
        val buildVersion = agentEntry.agent.buildVersion
        val agentId = agentEntry.agent.id
        logger.debug { "ensuring plugin with id $pluginId for agent(id=$agentId, version=$buildVersion)..." }
        agentEntry[pluginId] ?: agentEntry.get(pluginId) {
            val adminPluginData = adminData(agentId)
            val store = agentStores.agentStore(agentId)
            plugin.createInstance(
                agentInfo = agent,
                data = adminPluginData,
                sender = pluginSenders.sender(plugin.pluginBean.id),
                store = store
            )
        }.apply {
            logger.debug { "initializing plugin for agent(id=$agentId, version=$buildVersion)..." }
            initialize()
        }
    }

    private suspend fun AgentInfo.commitChanges() {
        persistToDatabase()
        notifyAgents(id)
    }

    internal suspend fun notifyAgents(agentId: String) {
        notifyAllAgents()
        notifySingleAgent(agentId)
    }

    private suspend fun wrapInstanceBusy(
        agentId: String,
        instanceId: String,
        block: suspend AgentWsSession.() -> Unit,
    ): Unit = entryOrNull(agentId)?.run {
        getInstanceState(instanceId)?.let { instanceState ->
            val id = agent.id
            updateInstanceStatus(instanceId, AgentStatus.BUSY).also { instance ->
                if (instance.all { it.value.status == AgentStatus.BUSY }) {
                    notifyAgents(id)
                    logger.debug { "Agent $id is busy." }
                }
                logger.trace { "Instance $instanceId of agent $id is busy." }
            }
            try {
                block(instanceState.agentWsSession)
            } finally {
                updateInstanceStatus(instanceId, AgentStatus.ONLINE).also {
                    notifyAgents(id)
                    logger.debug { "Agent $id is online." }
                }
            }
        } ?: logger.warn { "Instance $instanceId is not found" }
    } ?: logger.warn { "Agent $agentId not found" }
}

suspend fun AgentWsSession.setPackagesPrefixes(prefixes: List<String>) =
    sendToTopic<Communication.Agent.SetPackagePrefixesEvent, PackagesPrefixes>(
        PackagesPrefixes(prefixes)
    ).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic<Communication.Agent.LoadClassesDataEvent, String>("").await()

internal data class AgentKey(
    val agentId: String,
    val buildVersion: String,
)
