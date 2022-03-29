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
import com.epam.drill.admin.agent.AgentInfo
import com.epam.drill.admin.agent.config.*
import com.epam.drill.admin.agent.logging.*
import com.epam.drill.admin.agent.plugin.*
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
import com.epam.drill.admin.sync.*
import com.epam.drill.admin.util.*
import com.epam.drill.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.kodux.*
import io.ktor.application.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
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
            .map { it.info }
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
    private val configHandler by instance<ConfigHandler>()
    private val agentMonitor = MonitorMutex<String>()

    private val _instances = atomic(persistentHashMapOf<AgentKey, PersistentMap<String, InstanceState>>())

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
                    it.id to Agent(it)
                }
                val preparedMap = prepared.filter { it.id !in registeredMap }.associate {
                    it.id to Agent(it)
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
                agentStorage.put(dto.id, Agent(info))
                logger.debug { "Prepared agent ${dto.id}." }
            }
        } else null
    }

    internal suspend fun attach(
        config: CommonAgentConfig,
        needSync: Boolean,
        session: AgentWsSession,
    ): AgentInfo {
        logger.info { "Attaching agent: needSync=$needSync, config=$config" }
        val id = config.id
        configHandler.store(id, config.parameters)
        //todo implement merge of params in EPMDJ-8124
        val groupId = config.serviceGroupId
        logger.debug { "Group id '$groupId'" }
        if (groupId.isNotBlank()) {
            groupManager.syncOnAttach(groupId)
        }
        val oldInstanceIds = instanceIds(id)
        val buildVersion = config.buildVersion
        val agentKey = AgentKey(id, buildVersion)
        addInstanceId(agentKey, config.instanceId, session)
        val existingAgent = agentStorage[id]
        val currentInfo = existingAgent?.info
        val adminData = adminData(id)
        val isNewBuild = adminData.initBuild(buildVersion)
        loggingHandler.sync(id, session)
        //TODO agent instances
        return agentMonitor.withLock(id) {
            if (
                (oldInstanceIds.isEmpty() || currentInfo?.isRegistered == true) &&
                currentInfo?.buildVersion == buildVersion &&
                currentInfo.groupId == groupId &&
                currentInfo.agentVersion == config.agentVersion
            ) {
                logger.info { "agent($id, $buildVersion): reattaching to current build..." }
                notifySingleAgent(id)
                notifyAllAgents()
                currentInfo.plugins.initPlugins(existingAgent)
                if (needSync) app.launch {
                    currentInfo.sync(config.instanceId) // sync only existing info!
                } else session.syncPluginState()
                currentInfo.persistToDatabase()
                session.updateSessionHeader(adminData.settings.sessionIdHeaderName)
                currentInfo
            } else {
                logger.info { "agent($id, $buildVersion, ${config.instanceId}): attaching to new or stored build..." }
                val storedInfo: AgentInfo? = loadAgentInfo(id)
                val preparedInfo: AgentInfo? = preparedInfo(storedInfo, id)
                val existingInfo = (storedInfo ?: preparedInfo)?.copy(
                    buildVersion = config.buildVersion,
                    agentVersion = config.agentVersion
                )
                val info: AgentInfo = existingInfo ?: config.toAgentInfo()
                val entry = Agent(info)
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
        agent: Agent,
    ) {
        val agentInfo = agent.info
        val logPrefix by lazy(LazyThreadSafetyMode.NONE) {
            "Agent(id=${agentInfo.id}, buildVersion=${agentInfo.id}):"
        }
        forEach { pluginId ->
            plugins[pluginId]?.let { plugin ->
                ensurePluginInstance(agent, plugin)
                logger.info { "$logPrefix initialized plugin $pluginId" }
            } ?: logger.error { "$logPrefix plugin $pluginId not loaded!" }
        }
    }

    private fun addInstanceId(
        key: AgentKey,
        instanceId: String,
        session: AgentWsSession,
    ) {
        _instances.update {
            logger.info { "put new instance id '${instanceId}' with key $key instance status is ${AgentStatus.ONLINE}" }
            val current = it[key] ?: persistentMapOf()
            it.put(key, current + (instanceId to InstanceState(session, AgentStatus.ONLINE)))
        }
    }

    suspend fun register(
        agentId: String,
        dto: AgentRegistrationDto,
    ) = entryOrNull(agentId)?.let { agent ->
        val pluginsToAdd = dto.plugins.mapNotNull(plugins::get)
        val info: AgentInfo = agent.update { info ->
            info.copy(
                name = dto.name,
                environment = dto.environment,
                description = dto.description,
                isRegistered = true,
                plugins = pluginsToAdd.mapTo(mutableSetOf()) { it.pluginBean.id }
            )
        }.apply { persistToDatabase() }
        adminData(agentId).updateSettings(dto.systemSettings)
        pluginsToAdd.forEach { plugin -> ensurePluginInstance(agent, plugin) }
        instanceIds(agentId).keys.mapIndexed { index, instanceId ->
            info.sync(instanceId, index == 0)
        }
    }

    internal suspend fun removeInstance(
        key: AgentKey,
        instanceId: String,
    ) {
        val instances = _instances.updateAndGet { instances ->
            instances[key]?.let { instances.put(key, it - instanceId) } ?: instances
        }[key] ?: persistentMapOf()
        val id = key.agentId
        if (instances.isEmpty()) {
            logger.info { "Agent with id '${id}' was disconnected" }
            agentStorage.handleRemove(id)
            agentStorage.update()
        } else {
            notifySingleAgent(id)
            logger.info { "Instance '${instanceId}' of Agent '${id}' was disconnected" }
        }
    }

    internal fun instanceIds(
        agentId: String,
    ) = getOrNull(agentId)?.run {
        _instances.value[AgentKey(agentId, buildVersion)]
    } ?: persistentMapOf()

    internal suspend fun updateAgent(
        agentId: String,
        agentUpdateDto: AgentUpdateDto,
    ) = entryOrNull(agentId)?.also { agent ->
        agent.update {
            it.copy(
                name = agentUpdateDto.name,
                environment = agentUpdateDto.environment,
                description = agentUpdateDto.description
            )
        }.commitChanges()
        topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(agentId))
    }

    internal suspend fun resetAgent(agInfo: AgentInfo) = entryOrNull(agInfo.id)?.also { agent ->
        logger.debug { "Reset agent ${agInfo.id}" }
        agent.update {
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

    internal fun getOrNull(agentId: String) = agentStorage.targetMap[agentId]?.info

    internal operator fun get(agentId: String) = agentStorage.targetMap[agentId]?.info

    internal fun entryOrNull(agentId: String): Agent? = agentStorage.targetMap[agentId]

    internal fun allEntries(): Collection<Agent> = agentStorage.targetMap.values

    suspend fun addPlugins(
        agentId: String,
        pluginIds: Set<String>,
    ) = entryOrNull(agentId)!!.let { agent ->
        val existingIds = agent.info.plugins
        val pluginsToAdd = pluginIds.filter { it !in existingIds }.mapNotNull(plugins::get)
        if (pluginsToAdd.any()) {
            val updatedInfo = agent.update { info ->
                val updatedPlugins = info.plugins + pluginsToAdd.map { it.pluginBean.id }
                info.copy(
                    plugins = updatedPlugins
                )
            }
            supervisorScope {
                instanceIds(agentId).forEach { (instanceId, _) ->
                    pluginsToAdd.forEach { plugin ->
                        val pluginId = plugin.pluginBean.id
                        wrapInstanceBusy(agentId, instanceId) {
                            launch {
                                ensurePluginInstance(agent, plugin)
                                sendPlugin(plugin, updatedInfo).await()
                                enablePlugin(pluginId, updatedInfo).await()
                            }
                        }
                    }
                    topicResolver.sendToAllSubscribed(WsRoutes.AgentPlugins(agentId))
                }
                updatedInfo.persistToDatabase()
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

    //TODO EPMDJ-8233 move to the api
    private suspend fun AgentWsSession.enablePlugin(
        pluginId: String,
        agentInfo: AgentInfo,
    ): WsDeferred = sendToTopic<Communication.Plugin.ToggleEvent, TogglePayload>(
        message = TogglePayload(pluginId, true),
        topicName = "/agent/plugin/${pluginId}/toggle",
        callback = { logger.debug { "Enabled plugin $pluginId for ${agentInfo.debugString(instanceId)}" } }
    )

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
                wrapInstanceBusy(info.id, instanceId) {
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
        logger.info { "Sending plugin ${pb.id} to $debugStr" }
        val data = if (agentInfo.agentType == AgentType.JAVA) {
            plugin.agentPluginPart.readBytes()
        } else byteArrayOf()
        pb.checkSum = hex(sha1(data))
        //TODO EPMDJ-8233 move to the api
        return sendToTopic<Communication.Agent.PluginLoadEvent, com.epam.drill.common.PluginBinary>(
            message = com.epam.drill.common.PluginBinary(pb, data),
            topicName = "/agent/plugin/${pb.id}/loaded",
            callback = { logger.debug { "Sent plugin ${pb.id} to $debugStr" } }
        ).apply {
            await()
            logger.debug { "Sent data of plugin ${pb.id} to $debugStr" }
        }
    }

    internal fun agentsByGroup(groupId: String): List<Agent> = allEntries().filter {
        it.info.groupId == groupId
    }

    private suspend fun ensurePluginInstance(
        agent: Agent,
        plugin: Plugin,
    ): AdminPluginPart<*> = plugin.pluginBean.id.let { pluginId ->
        val buildVersion = agent.info.buildVersion
        val agentId = agent.info.id
        logger.info { "ensuring plugin with id $pluginId for agent(id=$agentId, version=$buildVersion)..." }
        agent[pluginId] ?: agent.get(pluginId) {
            val adminPluginData = adminData(agentId)
            val store = agentStores.agentStore(agentId)
            plugin.createInstance(
                agentInfo = info,
                data = adminPluginData,
                sender = pluginSenders.sender(plugin.pluginBean.id),
                store = store
            )
        }.apply {
            logger.info { "initializing ${plugin.pluginBean.id} plugin for agent(id=$agentId, version=$buildVersion)..." }
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
    ): Unit = getOrNull(agentId)?.run {
        val instanceKey = AgentKey(id, buildVersion)
        getInstanceState(instanceKey, instanceId)?.let { instanceState ->
            updateInstanceStatus(instanceKey, instanceId, AgentStatus.BUSY).also { instance ->
                if (instance.all { it.value.status == AgentStatus.BUSY }) {
                    notifyAgents(id)
                    logger.debug { "Agent $id is busy." }
                }
                logger.trace { "Instance $instanceId of agent $id is busy." }
            }
            try {
                block(instanceState.agentWsSession)
            } finally {
                updateInstanceStatus(instanceKey, instanceId, AgentStatus.ONLINE).also {
                    notifyAgents(id)
                    logger.debug { "Agent $id is online." }
                }
            }
        } ?: logger.warn { "Instance $instanceId is not found" }
    } ?: logger.warn { "Agent $agentId not found" }

    internal fun updateInstanceStatus(
        agentKey: AgentKey,
        instanceId: String,
        status: AgentStatus,
    ) = getInstanceState(agentKey, instanceId)?.let { instanceState ->
        _instances.updateAndGet { instances ->
            logger.trace { "instance $instanceId changed status, new status is $status" }
            instances[agentKey]?.let {
                instances.put(agentKey, it.put(instanceId, instanceState.copy(status = status)))
            } ?: instances
        }[agentKey]
    } ?: persistentMapOf()

    private fun getInstanceState(
        agentKey: AgentKey,
        instanceId: String,
    ) = _instances.value[agentKey]?.get(instanceId)

    fun getStatus(agentId: String): AgentStatus = instanceIds(agentId).let { instances ->
        if (getOrNull(agentId)?.isRegistered == true) {
            AgentStatus.OFFLINE.takeIf {
                instances.isEmpty() || instances.all { it.value.status == AgentStatus.OFFLINE }
            } ?: AgentStatus.ONLINE.takeIf {
                instances.any { it.value.status == AgentStatus.ONLINE }
            } ?: AgentStatus.BUSY
        } else {
            AgentStatus.NOT_REGISTERED.takeIf { instances.any() } ?: AgentStatus.OFFLINE
        }
    }
}

suspend fun AgentWsSession.setPackagesPrefixes(prefixes: List<String>) =
    sendToTopic<Communication.Agent.SetPackagePrefixesEvent, PackagesPrefixes>(
        PackagesPrefixes(prefixes)
    ).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic<Communication.Agent.LoadClassesDataEvent, String>("").await()

internal suspend fun StoreClient.storeMetadata(agentKey: AgentKey, metadata: Metadata) {
    store(StoredMetadata(agentKey, metadata))
}

internal suspend fun StoreClient.loadMetadata(
    agentKey: AgentKey,
): Metadata = findById<StoredMetadata>(agentKey)?.data ?: Metadata()

@Serializable
internal data class AgentKey(
    val agentId: String,
    val buildVersion: String,
)

internal data class InstanceState(
    val agentWsSession: AgentWsSession,
    val status: AgentStatus,
)
