package com.epam.drill.admin.endpoints

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.AgentInfo
import com.epam.drill.admin.agent.logging.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.store.*
import com.epam.drill.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.kodux.*
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
    private val serviceGroupManager by instance<ServiceGroupManager>()
    private val agentDataCache by instance<AgentDataCache>()
    private val notificationsManager by instance<NotificationManager>()
    private val loggingHandler by instance<LoggingHandler>()

    private val _instances = atomic(
        persistentHashMapOf<String, PersistentMap<String, AgentWsSession>>()
    )

    init {
        runBlocking {
            val prepared = commonStore.client.getAll<PreparedAgentData>()
            val agentEntryMap = prepared.associate { data ->
                agentDataCache[data.id] = AgentData(data.id, agentStores, data.dto.systemSettings)
                data.id to AgentEntry(data.dto.toAgentInfo(plugins))
            }
            if (agentEntryMap.any()) {
                agentStorage.init(agentEntryMap)
            }
        }
    }

    internal suspend fun prepare(
        dto: AgentCreationDto
    ): AgentInfo? = (get(dto.id) ?: loadAgentInfo(dto.id)).let { storedInfo ->
        if (storedInfo == null || storedInfo.agentVersion.none()) {
            logger.debug { "Preparing agent ${dto.id}..." }
            dto.toAgentInfo(plugins).also { info: AgentInfo ->
                agentDataCache[dto.id] = AgentData(dto.id, agentStores, dto.systemSettings)
                commonStore.client.store(
                    PreparedAgentData(id = dto.id, dto = dto)
                )
                agentStorage.put(dto.id, AgentEntry(info))
                logger.debug { "Prepared agent ${dto.id}." }
            }
        } else null
    }

    internal suspend fun attach(
        config: CommonAgentConfig,
        needSync: Boolean,
        session: AgentWsSession
    ): AgentInfo {
        logger.debug { "Attaching agent: needSync=$needSync, config=$config" }
        val id = config.id
        val serviceGroup = config.serviceGroupId
        logger.debug { "Service group id '$serviceGroup'" }
        if (serviceGroup.isNotBlank()) {
            serviceGroupManager.syncOnAttach(serviceGroup)
        }
        val oldInstanceIds = instanceIds(id)
        addInstanceId(id, config.instanceId, session)
        val existingEntry = agentStorage[id]
        val currentInfo = existingEntry?.agent
        val buildVersion = config.buildVersion
        val adminData = adminData(id)
        val isNewBuild = adminData.initBuild(buildVersion)
        loggingHandler.sync(id, session)
        //TODO agent instances
        return if (
            (oldInstanceIds.isEmpty() || config.instanceId in oldInstanceIds) &&
            currentInfo?.buildVersion == buildVersion &&
            currentInfo.serviceGroup == serviceGroup &&
            currentInfo.agentVersion == config.agentVersion
        ) {
            logger.debug { "agent($id, $buildVersion): reattaching to current build..." }
            notifySingleAgent(id)
            notifyAllAgents()
            if (needSync) app.launch {
                currentInfo.sync() // sync only existing info!
            }
            currentInfo.persistToDatabase()
            session.updateSessionHeader(adminData.settings.sessionIdHeaderName)
            currentInfo
        } else {
            logger.debug { "agent($id, $buildVersion): attaching to new or stored build..." }
            val storedInfo: AgentInfo? = loadAgentInfo(id)
            val preparedInfo: AgentInfo? = if (storedInfo == null) {
                commonStore.client.findById<PreparedAgentData>(id)?.let {
                    agentDataCache[id] = AgentData(id, agentStores, it.dto.systemSettings)
                    it.dto.toAgentInfo(plugins)
                }
            } else null
            val existingInfo = (storedInfo ?: preparedInfo)?.copy(
                buildVersion = config.buildVersion,
                agentVersion = config.agentVersion
            )
            val info: AgentInfo = existingInfo ?: config.toAgentInfo()
            val entry = AgentEntry(info)
            agentStorage.put(id, entry)?.also { oldEntry ->
                oldEntry.close()
            }
            existingInfo?.initPlugins(entry, isNewBuild)
            app.launch {
                existingInfo?.takeIf { needSync }?.sync() // sync only existing info!
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

    private suspend fun AgentInfo.initPlugins(
        agentEntry: AgentEntry,
        isNewBuild: Boolean
    ) {
        val enabledPlugins = plugins.mapNotNull { this@AgentManager.plugins[it]?.pluginBean }
        for (pluginMeta in enabledPlugins) {
            val pluginId = pluginMeta.id
            val plugin = this@AgentManager.plugins[pluginId]
            if (plugin != null) {
                if (isNewBuild) {
                    ensurePluginInstance(agentEntry, plugin)
                    logger.info { "Instance of plugin=$pluginId loaded from db, buildVersion=$buildVersion" }
                } else {
                    logger.info { "Instance of plugin=$pluginId not loaded (no data), buildVersion=$buildVersion" }
                }
            } else logger.error { "plugin=$pluginId not loaded!" }
        }
    }

    private fun addInstanceId(agentId: String, instanceId: String, session: AgentWsSession) {
        _instances.update { it.put(agentId, instanceIds(agentId, it) + (instanceId to session)) }
    }

    suspend fun register(
        agentId: String,
        dto: AgentRegistrationDto
    ) = entryOrNull(agentId)?.let { entry ->
        val pluginsToAdd = dto.plugins.mapNotNull(plugins::get)
        val info: AgentInfo = entry.updateAgent { info ->
            info.copy(
                name = dto.name,
                environment = dto.environment,
                description = dto.description,
                status = AgentStatus.BUSY,
                plugins = pluginsToAdd.mapTo(mutableSetOf()) { it.pluginBean.id }
            )
        }
        adminData(agentId).updateSettings(dto.systemSettings)
        pluginsToAdd.forEach { plugin -> ensurePluginInstance(entry, plugin) }
        info.sync()
    }

    internal suspend fun AgentInfo.removeInstance(instanceId: String, session: AgentWsSession) {
        val instances = _instances.updateAndGet {
            val instanceIds = it.getOrDefault(id, persistentHashMapOf())
            if (instanceIds[instanceId] === session) {
                it.put(id, instanceIds - instanceId)
            } else it
        }.getOrDefault(id, persistentHashMapOf())
        if (instances.isEmpty()) {
            logger.info { "Agent with id '${id}' was disconnected" }
            agentStorage.handleRemove(id)
        } else {
            notifySingleAgent(id)
            logger.info { "Instance '$instanceId' of Agent '${id}' was disconnected" }
        }
    }

    fun instanceIds(
        agentId: String,
        it: PersistentMap<String, PersistentMap<String, AgentWsSession>> = _instances.value
    ): PersistentMap<String, AgentWsSession> {
        return it[agentId].orEmpty().toPersistentMap()
    }

    internal suspend fun updateAgent(
        agentId: String,
        agentUpdateDto: AgentUpdateDto
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
                status = AgentStatus.NOT_REGISTERED
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

    fun agentSessions(k: String) = instanceIds(k).values

    fun buildVersionByAgentId(agentId: String) = getOrNull(agentId)?.buildVersion ?: ""

    operator fun contains(k: String) = k in agentStorage.targetMap

    internal fun getOrNull(agentId: String) = agentStorage.targetMap[agentId]?.agent

    internal operator fun get(agentId: String) = agentStorage.targetMap[agentId]?.agent

    internal fun entryOrNull(agentId: String): AgentEntry? = agentStorage.targetMap[agentId]

    internal fun allEntries(): Collection<AgentEntry> = agentStorage.targetMap.values

    suspend fun addPlugins(
        agentId: String,
        pluginIds: Set<String>
    ) = entryOrNull(agentId)!!.let { entry ->
        val existingIds = entry.agent.plugins
        val pluginsToAdd = pluginIds.filter { it !in existingIds }.mapNotNull(plugins::get)
        if (pluginsToAdd.any()) {
            val updatedInfo = entry.updateAgent { agent ->
                val updatedPlugins = agent.plugins + pluginsToAdd.map { it.pluginBean.id }
                agent.copy(
                    status = AgentStatus.BUSY,
                    plugins = updatedPlugins
                )
            }
            wrapBusy(updatedInfo) {
                supervisorScope {
                    agentSessions(id).forEach { session ->
                        pluginsToAdd.forEach { plugin ->
                            val pluginId = plugin.pluginBean.id
                            launch {
                                session.sendPlugin(plugin, updatedInfo).await()
                                session.enablePlugin(pluginId, agentId).await()
                                topicResolver.sendToAllSubscribed(WsRoutes.AgentPlugins(id))
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun addPlugins(
        agentInfos: Iterable<AgentInfo>,
        pluginIds: Set<String>
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

    private suspend fun wrapBusy(
        ai: AgentInfo,
        block: suspend AgentInfo.() -> Unit
    ) = entryOrNull(ai.id)?.also { entry ->
        val busy = entry.updateAgent {
            it.copy(status = AgentStatus.BUSY)
        }.apply { commitChanges() }
        logger.debug { "Agent ${ai.id} is busy." }

        try {
            block(busy)
        } finally {
            entry.updateAgent {
                it.copy(status = AgentStatus.ONLINE)
            }.apply { commitChanges() }
            logger.debug { "Agent ${ai.id} is online." }
        }
    }

    internal fun adminData(agentId: String): AgentData = agentDataCache.getOrPut(agentId) {
        AgentData(
            agentId,
            agentStores,
            SystemSettingsDto(packages = app.drillDefaultPackages)
        )
    }

    private suspend fun disableAllPlugins(agentId: String) {
        logger.debug { "Reset all plugins for agent with id $agentId" }
        getOrNull(agentId)?.plugins?.forEach { pluginId ->
            agentSessions(agentId).applyEach {
                sendToTopic<Communication.Plugin.ToggleEvent>(
                    com.epam.drill.common.TogglePayload(pluginId, false)
                )
            }
        }
        logger.debug { "All plugins for agent with id $agentId were disabled" }
    }

    private suspend fun AgentWsSession.enableAllPlugins(agentId: String) {
        logger.debug { "Enabling all plugins for agent with id $agentId" }
        getOrNull(agentId)?.plugins?.let { pluginIds ->
            pluginIds.map { pluginId ->
                logger.debug { "Enabling plugin $pluginId for agent $agentId..." }
                enablePlugin(pluginId, agentId)
            }.forEach { it.await() }
        }
        logger.debug { "All plugins for agent with id $agentId were enabled" }
    }

    private suspend fun AgentWsSession.enablePlugin(
        pluginId: String,
        agentId: String
    ): WsDeferred = sendToTopic<Communication.Plugin.ToggleEvent>(
        com.epam.drill.common.TogglePayload(pluginId, true)
    ) {
        logger.debug { "Enabled plugin $pluginId for agent $agentId" }
    }

    private suspend fun loadAgentInfo(agentId: String): AgentInfo? = commonStore.client.findById(agentId)

    private suspend fun AgentInfo.persistToDatabase() {
        commonStore.client.store(this)
    }

    private suspend fun AgentWsSession.configurePackages(prefixes: List<String>) {
        setPackagesPrefixes(prefixes)
    }

    private suspend fun AgentInfo.sync() {
        if (status != AgentStatus.NOT_REGISTERED) {
            val instanceIds = instanceIds(id)
            if (instanceIds.any()) {
                wrapBusy(this) {
                    logger.debug { "Agent $id: starting sync for instances ${instanceIds.keys}..." }
                    val info = this
                    val duration = measureTime {
                        instanceIds.values.applyEach {
                            val settings = adminData(id).settings
                            configurePackages(settings.packages)
                            sendPlugins(info)
                            updateSessionHeader(settings.sessionIdHeaderName)
                            triggerClassesSending()
                            enableAllPlugins(id)
                        }
                    }
                    logger.info { "Agent $id: sync took: $duration." }
                    topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(id))
                    logger.debug { "Agent $id: sync finished." }
                }
            } else logger.error { "Agent $id: no instances to sync!" }
        } else logger.warn { "Agent $id: cannot sync, status is ${AgentStatus.NOT_REGISTERED}." }
    }

    private suspend fun AgentWsSession.updateSessionHeader(sessionIdHeaderName: String) {
        sendToTopic<Communication.Agent.ChangeHeaderNameEvent>(sessionIdHeaderName.toLowerCase())
    }

    suspend fun updateSystemSettings(agentId: String, settings: SystemSettingsDto) {
        val adminData = adminData(agentId)
        getOrNull(agentId)?.let {
            wrapBusy(it) {
                adminData.updateSettings(settings) { oldSettings ->
                    agentSessions(agentId).applyEach {
                        if (oldSettings.sessionIdHeaderName != settings.sessionIdHeaderName) {
                            updateSessionHeader(settings.sessionIdHeaderName)
                        }
                        if (oldSettings.packages != settings.packages) {
                            disableAllPlugins(agentId)
                            configurePackages(settings.packages)
                            triggerClassesSending()
                            entryOrNull(agentId)?.applyPackagesChanges()
                            enableAllPlugins(id)
                        }
                    }
                }
            }
        }
    }

    internal suspend fun updateSystemSettings(
        agentInfos: Iterable<AgentInfo>,
        systemSettings: SystemSettingsDto
    ): Set<String> = supervisorScope {
        agentInfos.map { info ->
            val agentId = info.id
            val handler = CoroutineExceptionHandler { _, e ->
                logger.error(e) { "Error updating agent $agentId" }
            }
            async(handler) {
                updateSystemSettings(agentId, systemSettings)
                agentId
            }
        }
    }.filterNot { it.isCancelled }.mapTo(mutableSetOf()) { it.await() }

    private suspend fun AgentWsSession.sendPlugins(info: AgentInfo) {
        logger.debug { "Sending ${info.plugins.count()} plugins to agent ${info.id}" }
        info.plugins.mapNotNull(plugins::get).map { pb ->
            sendPlugin(pb, info)
        }.forEach { it.await() }
        logger.debug { "Sent plugins ${info.plugins} to agent ${info.id}" }
    }

    private suspend fun AgentWsSession.sendPlugin(
        plugin: Plugin,
        agentInfo: AgentInfo
    ): WsDeferred {
        val pb = plugin.pluginBean
        logger.debug { "Sending plugin ${pb.id} to agent ${agentInfo.id}" }
        val data = if (agentInfo.agentType == AgentType.JAVA) {
            plugin.agentPluginPart.readBytes()
        } else byteArrayOf()
        pb.checkSum = hex(sha1(data))
        return async(
            topicName = "/agent/plugin/${pb.id}/loaded",
            callback = { logger.debug { "Sent plugin ${pb.id} to agent ${agentInfo.id}" } }
        ) { //TODO move to the api
            sendToTopic<Communication.Agent.PluginLoadEvent>(
                com.epam.drill.common.PluginBinary(pb, data)
            ).await()
            logger.debug { "Sent data of plugin ${pb.id} to agent ${agentInfo.id}" }
        }
    }

    internal fun serviceGroup(serviceGroupId: String): List<AgentEntry> = allEntries().filter {
        it.agent.serviceGroup == serviceGroupId
    }

    internal suspend fun ensurePluginInstance(
        agentEntry: AgentEntry,
        plugin: Plugin
    ): AdminPluginPart<*> {
        return agentEntry.get(plugin.pluginBean.id) {
            val adminPluginData = adminData(agent.id)
            val store = agentStores.agentStore(agent.id)
            val pluginInstance = plugin.createInstance(
                agentInfo = agent,
                data = adminPluginData,
                sender = pluginSenders.sender(plugin.pluginBean.id),
                store = store
            )
            pluginInstance.initialize()
            pluginInstance
        }
    }

    internal suspend fun AgentInfo.commitChanges() {
        persistToDatabase()
        notifyAllAgents()
        notifySingleAgent(id)
    }
}

suspend fun AgentWsSession.setPackagesPrefixes(prefixes: List<String>) =
    sendToTopic<Communication.Agent.SetPackagePrefixesEvent>(
        com.epam.drill.common.PackagesPrefixes(prefixes)
    ).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic<Communication.Agent.LoadClassesDataEvent>().await()
