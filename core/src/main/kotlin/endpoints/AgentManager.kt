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
package com.epam.drill.admin.endpoints

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.AgentInfo
import com.epam.drill.admin.agent.config.*
import com.epam.drill.admin.agent.logging.*
import com.epam.drill.admin.agent.plugin.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.build.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
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
import com.epam.drill.admin.websocket.*
import com.epam.drill.admin.websocket.agentKeyPattern
import com.epam.drill.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.dsm.*
import com.epam.dsm.find.*
import io.ktor.application.*
import io.ktor.util.*
import kotlinx.coroutines.*
import mu.*
import org.kodein.di.*
import java.util.*
import kotlin.time.*


class AgentManager(override val di: DI) : DIAware {
    private val logger = KotlinLogging.logger {}

    internal val agentStorage by instance<AgentStorage>()

    internal val plugins by instance<Plugins>()

    internal val activeAgents: List<AgentInfo>
        get() = agentStorage.values
            .map { it.info }
            .filter { buildManager.instanceIds(it.id).isNotEmpty() }
            .sortedWith(compareBy(AgentInfo::id))

    private val app by instance<Application>()
    private val topicResolver by instance<TopicResolver>()
    private val pluginSenders by instance<PluginSenders>()
    private val groupManager by instance<GroupManager>()
    private val agentDataCache by instance<AgentDataCache>()
    private val notificationsManager by instance<NotificationManager>()
    private val loggingHandler by instance<LoggingHandler>()
    private val configHandler by instance<ConfigHandler>()
    private val buildManager by instance<BuildManager>()
    private val cacheService by instance<CacheService>()
    private val agentMonitor = MonitorMutex<String>()

    init {
        trackTime("loadingAgents") {
            runBlocking {
                val registered = adminStore.getAll<AgentInfo>()
                val prepared = adminStore.getAll<PreparedAgentData>().map { data ->
                    agentDataCache[data.id] = AgentData(data.id, data.dto.systemSettings)
                    data.dto.toAgentInfo(plugins)
                }
                val registeredMap = registered.associate {
                    buildManager.buildData(it.id).initBuild(it.build.version)
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
        if (storedInfo == null || storedInfo.build.agentVersion.none() || storedInfo.agentStatus == AgentStatus.NOT_REGISTERED) {
            logger.debug { "Preparing agent ${dto.id}..." }
            dto.toAgentInfo(plugins).also { info: AgentInfo ->
                agentDataCache[dto.id] = AgentData(dto.id, dto.systemSettings)
                adminStore.store(
                    PreparedAgentData(id = dto.id, dto = dto)
                )
                adminStore.deleteById<AgentInfo>(dto.id)
                agentStorage.put(dto.id, Agent(info))
                logger.debug { "Prepared agent ${dto.id}." }
            }
        } else null
    }

    internal suspend fun removePreregisteredAgent(
        agentId: String,
    ): Boolean = (get(agentId) ?: loadAgentInfo(agentId)).let { storedInfo ->
        val agentStatus = storedInfo?.agentStatus
        if (storedInfo == null || agentStatus == AgentStatus.PREREGISTERED) {
            adminStore.deleteById<PreparedAgentData>(agentId)
            agentStorage.remove(agentId)
            logger.debug { "Pre registered Agent $agentId has been completely removed." }
            return true
        }
        return false
    }

    internal suspend fun removeOfflineAgent(
        agentId: String,
    ) = trackTime("Remove $agentId") {
        (get(agentId) ?: loadAgentInfo(agentId))?.let { storedInfo ->
            adminStore.executeInAsyncTransaction {
                storedInfo.plugins.forEach { pluginId ->
                    pluginStoresDSM(pluginId).deleteBy<Stored> {
                        (Stored::id.startsWith("$agentPrefix$agentId"))
                    }
                    (cacheService as? MapDBCacheService)?.clear(AgentCacheKey(pluginId, agentId))
                }
                deleteBy<AgentBuildData> { FieldPath(AgentBuildData::id, AgentBuildId::agentId) eq agentId }
                deleteBy<StoredCodeData> { FieldPath(StoredCodeData::id, AgentBuildId::agentId) eq agentId }
                deleteBy<AgentMetadata> { FieldPath(AgentMetadata::id, AgentBuildId::agentId) eq agentId }
                deleteById<AgentInfo>(agentId)
                deleteById<AgentDataSummary>(agentId)
                buildManager.buildData(agentId).agentBuildManager.deleteAll()
                configHandler.remove(agentId)
            }
            agentStorage.remove(agentId)
        }
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
        val oldInstanceIds = buildManager.instanceIds(id)
        val buildVersion = config.buildVersion
        val agentBuildKey = AgentBuildKey(id, buildVersion)
        buildManager.addBuildInstance(agentBuildKey, config.instanceId, session)
        loggingHandler.sync(id, session)
        //TODO agent instances
        return agentMonitor.withLock(id) {
            val existingAgent = agentStorage[id]
            val currentInfo = existingAgent?.info
            val adminData = buildManager.buildData(id)
            val isNewBuild = adminData.initBuild(buildVersion)
            if (
                (oldInstanceIds.isEmpty() || currentInfo?.agentStatus == AgentStatus.REGISTERED) &&
                currentInfo?.build?.version == buildVersion &&
                currentInfo.groupId == groupId &&
                currentInfo.build.agentVersion == config.agentVersion
            ) {
                logger.info { "agent($id, $buildVersion): reattaching to current build..." }
                notifySingleAgent(id)
                notifyAllAgents()
                currentInfo.plugins.initPlugins(existingAgent)
                app.launch {
                    if (needSync) currentInfo.sync(config.instanceId) // sync only existing info!
                    currentInfo.plugins.forEach { existingAgent[it]?.syncState() }
                }
                currentInfo.persistToDatabase()
                session.updateSessionHeader(adminData.settings.sessionIdHeaderName)
                currentInfo
            } else {
                logger.info { "agent($id, $buildVersion, ${config.instanceId}): attaching to new or stored build..." }
                val storedInfo: AgentInfo? = loadAgentInfo(id)
                val preparedInfo: AgentInfo? = preparedInfo(id, storedInfo)?.copy(
                    agentStatus = AgentStatus.REGISTERING
                )
                val existingInfo = (storedInfo ?: preparedInfo)?.run {
                    copy(
                        build = build.copy(
                            version = config.buildVersion,
                            agentVersion = config.agentVersion,
                        )
                    )
                }
                val info: AgentInfo = existingInfo ?: config.toAgentInfo()
                val entry = Agent(info)
                agentStorage.put(id, entry)?.also { oldEntry ->
                    oldEntry.close() // second
                }
                buildManager.notifyBuild(agentBuildKey)
                existingInfo?.plugins?.initPlugins(entry) // first
                app.launch {
                    existingInfo?.takeIf { needSync }?.sync(config.instanceId, true) // sync only existing info!
                    storedInfo?.plugins?.forEach { entry[it]?.syncState() }
                    if (isNewBuild && currentInfo != null) {
                        notificationsManager.saveNewBuildNotification(info)
                    }
                }
                preparedInfo?.let {
                    adminStore.deleteById<PreparedAgentData>(it.id)
                    entry.update { info.copy(agentStatus = AgentStatus.REGISTERED) }.commitChanges()
                }
                session.updateSessionHeader(adminData.settings.sessionIdHeaderName)
                info
            }
        }
    }

    private suspend fun preparedInfo(
        id: String,
        storedInfo: AgentInfo? = null,
    ) = if (storedInfo == null) {
        adminStore.findById<PreparedAgentData>(id)?.let {
            agentDataCache[id] = AgentData(id, it.dto.systemSettings)
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
                agentStatus = AgentStatus.REGISTERING,
                plugins = pluginsToAdd.mapTo(mutableSetOf()) { it.pluginBean.id }
            )
        }.apply { notifyAgents(id) }
        buildManager.buildData(agentId).updateSettings(dto.systemSettings)
        pluginsToAdd.forEach { plugin -> ensurePluginInstance(agent, plugin) }
        buildManager.instanceIds(agentId).keys.mapIndexed { index, instanceId ->
            info.sync(instanceId, index == 0)
        }
        agent.update { it.copy(agentStatus = AgentStatus.REGISTERED) }.apply { commitChanges() }
        info.sendAgentRegisterAction()
    }


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
        topicResolver.sendToAllSubscribed(WsRoutes.AgentBuildsSummary(agentId))
    }

    internal suspend fun resetAgent(agInfo: AgentInfo) = entryOrNull(agInfo.id)?.also { agent ->
        logger.debug { "Reset agent ${agInfo.id}" }
        agent.update {
            it.copy(
                name = "",
                environment = "",
                description = "",
                agentStatus = AgentStatus.NOT_REGISTERED
            )
        }.commitChanges()
        logger.debug { "Agent ${agInfo.id} has been reset" }
        topicResolver.sendToAllSubscribed(WsRoutes.AgentBuildsSummary(agInfo.id))
    }

    private suspend fun notifyAllAgents() {
        agentStorage.update()
    }

    private suspend fun notifySingleAgent(agentId: String) {
        agentStorage.singleUpdate(agentId)
    }

    fun buildVersionByAgentId(agentId: String) = getOrNull(agentId)?.build?.version ?: ""

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
                buildManager.instanceIds(agentId).forEach { (instanceId, _) ->
                    pluginsToAdd.forEach { plugin ->
                        val pluginId = plugin.pluginBean.id
                        buildManager.processInstance(agentId, instanceId) {
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

    private suspend fun loadAgentInfo(agentId: String): AgentInfo? = adminStore.findById(agentId)

    private suspend fun AgentInfo.persistToDatabase() {
        adminStore.store(this)
    }

    private suspend fun AgentWsSession.configurePackages(prefixes: List<String>) {
        setPackagesPrefixes(prefixes)
    }

    private suspend fun AgentInfo.sync(
        instanceId: String,
        needClassSending: Boolean = false,
    ) {
        val agentDebugStr = debugString(instanceId)
        if (agentStatus != AgentStatus.NOT_REGISTERED) {
            logger.debug { "$agentDebugStr: starting sync for instance $instanceId..." }
            val info = this
            val duration = measureTime {
                buildManager.processInstance(info.id, instanceId) {
                    val settings = buildManager.buildData(id).settings
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
            topicResolver.sendToAllSubscribed(WsRoutes.AgentBuildsSummary(id))
            logger.debug { "$agentDebugStr: sync finished." }
        } else logger.warn { "$agentDebugStr: cannot sync, status is ${AgentStatus.NOT_REGISTERED}." }
    }

    private suspend fun AgentWsSession.updateSessionHeader(sessionIdHeaderName: String) {
        sendToTopic<Communication.Agent.ChangeHeaderNameEvent, String>(sessionIdHeaderName.lowercase(Locale.getDefault()))
    }

    suspend fun updateSystemSettings(agentId: String, settings: SystemSettingsDto) {
        getOrNull(agentId)?.let { info ->
            buildManager.updateSystemSettings(agentId, settings) { oldSettings ->
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
                updateSystemSettings(
                    agentId, systemSettings.copy(
                        targetHost = buildManager.buildData(agentId).settings.targetHost
                    )
                )
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
        val agentId = agent.info.id
        val buildVersion = agent.info.build.version
        logger.info { "ensuring plugin with id $pluginId for agent(id=$agentId, version=$buildVersion)..." }
        val pluginStore = pluginStoresDSM(pluginId)
        agent[pluginId] ?: agent.get(pluginId) {
            val agentData = buildManager.buildData(agentId)
            plugin.createInstance(
                agentInfo = info,
                data = agentData,
                sender = pluginSenders.sender(plugin.pluginBean.id),
                store = pluginStore
            )
        }.apply {
            logger.info { "initializing ${plugin.pluginBean.id} plugin for agent(id=$agentId, version=$buildVersion)..." }
            initialize()
        }
    }

    private suspend fun BuildManager.updateSystemSettings(
        agentId: String,
        settings: SystemSettingsDto,
        block: suspend AgentWsSession.(SystemSettingsDto) -> Unit,
    ) {
        buildData(agentId).updateSettings(settings) { oldSettings ->
            instanceIds(agentId).forEach { (instanceId, _) ->
                processInstance(agentId, instanceId) {
                    block(oldSettings)
                }
            }
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


}

suspend fun AgentWsSession.setPackagesPrefixes(prefixes: List<String>) =
    sendToTopic<Communication.Agent.SetPackagePrefixesEvent, PackagesPrefixes>(
        PackagesPrefixes(prefixes)
    ).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic<Communication.Agent.LoadClassesDataEvent, String>("").await()

internal suspend fun StoreClient.storeMetadata(agentBuildKey: AgentBuildKey, metadata: Metadata) {
    store(AgentMetadata(agentBuildKey, metadata))
}

internal suspend fun StoreClient.loadAgentMetadata(
    agentBuildKey: AgentBuildKey,
): AgentMetadata = findById(agentBuildKey) ?: AgentMetadata(agentBuildKey)

data class InstanceState(
    val agentWsSession: AgentWsSession,
    val status: BuildStatus,
)
