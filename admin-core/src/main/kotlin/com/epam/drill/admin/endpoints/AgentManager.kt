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
import com.epam.drill.admin.agent.config.ConfigHandler
import com.epam.drill.admin.agent.logging.LoggingHandler
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.build.AgentBuildData
import com.epam.drill.admin.build.AgentBuildId
import com.epam.drill.admin.cache.CacheService
import com.epam.drill.admin.cache.impl.MapDBCacheService
import com.epam.drill.admin.config.drillConfig
import com.epam.drill.admin.group.GroupManager
import com.epam.drill.admin.notification.NotificationManager
import com.epam.drill.admin.plugin.AgentCacheKey
import com.epam.drill.admin.plugin.PluginSenders
import com.epam.drill.admin.plugins.Plugin
import com.epam.drill.admin.plugins.Plugins
import com.epam.drill.admin.router.WsRoutes
import com.epam.drill.admin.storage.AgentStorage
import com.epam.drill.admin.store.Stored
import com.epam.drill.admin.store.adminStore
import com.epam.drill.admin.store.pluginStoresDSM
import com.epam.drill.admin.sync.MonitorMutex
import com.epam.drill.admin.util.trackTime
import com.epam.drill.admin.websocket.agentPrefix
import com.epam.drill.plugin.api.end.AdminPluginPart
import com.epam.dsm.StoreClient
import com.epam.dsm.deleteBy
import com.epam.dsm.deleteById
import com.epam.dsm.find.FieldPath
import io.ktor.application.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.util.*

/**
 * Service for managing agents
 */
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

    internal suspend fun removeAgent(
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
                deleteBy<AgentMetadata> { FieldPath(AgentMetadata::id, AgentBuildId::agentId) eq agentId }
                deleteById<AgentInfo>(agentId)
                deleteById<AgentDataSummary>(agentId)
                buildManager.buildData(agentId).agentBuildManager.deleteAll()
                configHandler.remove(agentId)
            }
            agentStorage.remove(agentId)
        }
    }

    /**
     * Actions taken when establishing a connection with an agent
     *
     * @param config the configuration of the agent
     * @param session the current WebSocket session of the agent
     * @return the agent information
     *
     * @features Agent attaching
     */
    internal suspend fun attach(
        config: CommonAgentConfig,
    ): AgentInfo {
        logger.info { "Attaching agent: config=$config" }
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
        buildManager.addBuildInstance(agentBuildKey, config.instanceId)
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
                currentInfo.sync(config.instanceId) // sync only existing info!
                currentInfo.persistToDatabase()
                currentInfo
            } else {
                logger.info { "agent($id, $buildVersion, ${config.instanceId}): attaching to new or stored build..." }
                val storedInfo: AgentInfo? = loadAgentInfo(id)
                val existingInfo = storedInfo?.run {
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
                existingInfo?.sync(config.instanceId) // sync only existing info!
                if (isNewBuild && currentInfo != null) {
                    notificationsManager.saveNewBuildNotification(info)
                }
                if (info.agentStatus == AgentStatus.NOT_REGISTERED || info.agentStatus == AgentStatus.PREREGISTERED) {
                    app.launch {
                        val dto = AgentRegistrationDto(
                            name = info.name,
                            description = info.description,
                            environment = info.environment,
                            systemSettings = SystemSettingsDto(
                                packages = config.packagesPrefixes.packagesPrefixes
                            ),
                            plugins = listOf("test2code")
                        )
                        register(config.id, dto)
                    }
                }
                info
            }

        }
    }


    /**
     * Create all plugin instances
     * @param agent the agent
     * @features Agent registration, Agent attaching
     */
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

    /**
     * Register a new agent
     *
     * @param agentId Agent ID
     * @param dto Registration form
     * @return Model of registered agent
     *
     * @features Agent registration
     */
    suspend fun register(
        agentId: String,
        dto: AgentRegistrationDto,
    ) = entryOrNull(agentId)?.let { agent ->
        val info: AgentInfo = agent.update { info ->
            info.copy(
                name = dto.name,
                agentStatus = AgentStatus.REGISTERING,
                plugins = setOf("test2code")
            )
        }.apply { notifyAgents(id) }
        // save name and plugin to
        buildManager.buildData(agentId).updateSettings(dto.systemSettings)
        ensurePluginInstance(agent, plugins["test2code"]!!)
        buildManager.instanceIds(agentId).keys.mapIndexed { index, instanceId ->
            info.sync(instanceId)
        }
        agent.update { it.copy(agentStatus = AgentStatus.REGISTERED) }.apply { commitChanges() }
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

    /**
     * Get the agent information from DB
     * @param agentId the agent ID
     * @return the agent information
     * @features Agent registration, Agent attaching
     */
    private suspend fun loadAgentInfo(agentId: String): AgentInfo? = adminStore.findById(agentId)

    /**
     * Persist the agent information into DB
     *
     * @features Agent registration
     */
    private suspend fun AgentInfo.persistToDatabase() {
        adminStore.store(this)
    }


    /**
     * Synchronize the agent information with the agent
     * @param instanceId the agent instance ID
     * @param needClassSending the sign of the need to load classes from the agent
     * @features Agent registration, Agent attaching
     */
    private suspend fun AgentInfo.sync(
        instanceId: String,
    ) {
        val agentDebugStr = debugString(instanceId)
        logger.debug { "$agentDebugStr: starting sync for instance $instanceId..." }
//        TODO
//        val info = this
//        val duration = measureTime {
//            buildManager.processInstance(info.id, instanceId) {
//                val settings = buildManager.buildData(id).settings
//                if (needClassSending) {
//                    this.updateSessionHeader(settings.sessionIdHeaderName)
//                    this.enableAllPlugins(info)
//                }
//            }
//        }
//        logger.info { "$agentDebugStr: sync took: $duration." }
        topicResolver.sendToAllSubscribed(WsRoutes.AgentBuildsSummary(id))
        logger.debug { "$agentDebugStr: sync finished." }
    }

    internal fun agentsByGroup(groupId: String): List<Agent> = allEntries().filter {
        it.info.groupId == groupId
    }

    /**
     * Create the admin part of the plugin
     * @param agent the agent
     * @param plugin the plugin structure
     * @return the admin part of the plugin
     * @features Agent registration, Agent attaching
     */
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
                store = pluginStore,
                config = app.drillConfig
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
}



internal suspend fun StoreClient.loadAgentMetadata(
    agentBuildKey: AgentBuildKey,
): AgentMetadata = findById(agentBuildKey) ?: AgentMetadata(agentBuildKey)

class InstanceState
