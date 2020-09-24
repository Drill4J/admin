package com.epam.drill.admin.endpoints

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.logging.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.storage.*
import com.epam.drill.api.*
import com.epam.drill.common.*
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

private val logger = KotlinLogging.logger {}

class AgentManager(override val kodein: Kodein) : KodeinAware {

    val app by instance<Application>()
    val agentStorage by instance<AgentStorage>()
    val plugins by instance<Plugins>()

    val activeAgents: List<AgentInfo>
        get() = agentStorage.values
            .map { it.agent }
            .filter { instances(it.id).isNotEmpty() }
            .sortedWith(compareBy(AgentInfo::id))

    private val topicResolver by instance<TopicResolver>()
    private val store by instance<StoreManager>()
    private val pluginSenders by instance<PluginSenders>()
    private val serviceGroupManager by instance<ServiceGroupManager>()
    private val adminDataVault by instance<AgentDataCache>()
    private val notificationsManager by instance<NotificationManager>()
    private val loggingHandler by instance<LoggingHandler>()

    private val _instances = atomic(persistentHashMapOf<String, PersistentMap<String, AgentInstance>>())

    suspend fun prepare(
        dto: AgentCreationDto
    ): AgentInfo? = when (store.agentStore(dto.id).findById<AgentInfo>(dto.id)) {
        null -> {
            logger.debug { "Preparing agent ${dto.id}..." }
            dto.toAgentInfo(plugins).also { info: AgentInfo ->
                val entry = AgentEntry(info)
                agentStorage.put(dto.id, entry)
                info.persistToDatabase()
                logger.debug { "Prepared agent ${dto.id}." }
            }
        }
        else -> null
    }

    suspend fun attach(config: AgentConfig, needSync: Boolean, session: AgentWsSession): AgentInfo {
        logger.debug { "Attaching agent: needSync=$needSync, config=$config" }
        val id = config.id
        val serviceGroup = config.serviceGroupId
        logger.debug { "Service group id '$serviceGroup'" }
        if (serviceGroup.isNotBlank()) {
            serviceGroupManager.syncOnAttach(serviceGroup)
        }
        val oldInstances = instances(id)
        addInstance(id, AgentInstance(config.instanceId, session, config.agentVersion))
        val existingEntry = agentStorage.targetMap[id]
        val currentInfo = existingEntry?.agent
        val buildVersion = config.buildVersion
        val adminData = adminData(id)
        adminData.initBuild(buildVersion)
        loggingHandler.sync(id, session)
        //TODO agent instances
        return if (
            (oldInstances.isEmpty() || config.instanceId in oldInstances) &&
            currentInfo?.buildVersion == buildVersion &&
            currentInfo.serviceGroup == serviceGroup &&
            currentInfo.agentVersion == config.agentVersion
        ) {
            notifySingleAgent(id)
            notifyAllAgents()
            app.launch {
                currentInfo.sync(needSync) // sync only existing info!
            }
            currentInfo.persistToDatabase()
            session.updateSessionHeader(adminData.settings.sessionIdHeaderName)
            currentInfo
        } else {
            val store = store.agentStore(id)
            val storedInfo = store.findById<AgentInfo>(id)?.copy(
                buildVersion = config.buildVersion,
                agentVersion = config.agentVersion
            )
            val info = storedInfo ?: config.toAgentInfo()
            val entry = AgentEntry(info)
            agentStorage.put(id, entry)
            storedInfo?.initPlugins(entry)
            app.launch {
                storedInfo?.sync(needSync) // sync only existing info!
                notificationsManager.handleNewBuildNotification(info)
            }
            info.persistToDatabase()
            session.updateSessionHeader(adminData.settings.sessionIdHeaderName)
            info
        }
    }

    private suspend fun AgentInfo.initPlugins(agentEntry: AgentEntry) {
        val enabledPlugins = plugins.filter { it.enabled }
        for (pluginMeta in enabledPlugins) {
            val pluginId = pluginMeta.id
            val plugin = this@AgentManager.plugins[pluginId]
            if (plugin != null) {
                if (adminDataVault[id]?.buildManager?.lastBuild == buildVersion) {
                    ensurePluginInstance(agentEntry, plugin)
                    logger.info { "Instance of plugin=$pluginId loaded from db, buildVersion=$buildVersion" }
                } else {
                    logger.info { "Instance of plugin=$pluginId not loaded (no data), buildVersion=$buildVersion" }
                }
            } else logger.error { "plugin=$pluginId not loaded!" }
        }
    }

    private fun addInstance(agentId: String, instance: AgentInstance) {
        _instances.update { it.put(agentId, instances(agentId, it) + (instance.id to instance)) }
    }

    suspend fun AgentInfo.removeInstance(instanceId: String) {
        _instances.update { it.put(id, instances(id, it) - instanceId) }
        if (instances(id).isEmpty()) {
            logger.info { "Agent with id '${id}' was disconnected" }
            agentStorage.handleRemove(id)
        } else {
            notifySingleAgent(id)
            logger.info { "Instance '$instanceId' of Agent '${id}' was disconnected" }
        }
    }

    fun instances(
        agentId: String,
        it: PersistentMap<String, PersistentMap<String, AgentInstance>> = _instances.value
    ): PersistentMap<String, AgentInstance> {
        return it[agentId].orEmpty().toPersistentMap()
    }

    suspend fun updateAgent(agentId: String, agentUpdateDto: AgentUpdateDto) {
        logger.debug { "Agent with id $agentId update with agent info $agentUpdateDto" }
        getOrNull(agentId)?.apply {
            name = agentUpdateDto.name
            environment = agentUpdateDto.environment
            description = agentUpdateDto.description
            commitChanges()
        }
        logger.debug { "Agent with id $agentId updated" }
        topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(agentId))
    }

    suspend fun updateAgentPluginConfig(agentId: String, pc: PluginConfig): Boolean {
        logger.debug { "Update plugin config for agent with id $agentId" }
        val result = getOrNull(agentId)?.let { agentInfo ->
            agentInfo.plugins.find { it.id == pc.id }?.let { plugin ->
                if (plugin.config != pc.data) {
                    plugin.config = pc.data
                    agentInfo.commitChanges()
                }
            }
        } != null
        logger.debug { "" }
        return result
    }

    suspend fun resetAgent(agInfo: AgentInfo) {
        logger.debug { "Reset agent with id ${agInfo.name}" }
        getOrNull(agInfo.id)?.apply {
            name = ""
            environment = ""
            description = ""
            status = AgentStatus.NOT_REGISTERED
            commitChanges()
        }
        logger.debug { "Agent with id ${agInfo.name} has been reset" }
        topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(agInfo.id))
    }

    private suspend fun notifyAllAgents() {
        agentStorage.update()
    }

    private suspend fun notifySingleAgent(agentId: String) {
        agentStorage.singleUpdate(agentId)
    }

    fun agentSessions(k: String) = instances(k).map { it.value.session }

    fun buildVersionByAgentId(agentId: String) = getOrNull(agentId)?.buildVersion ?: ""

    operator fun contains(k: String) = k in agentStorage.targetMap

    fun getOrNull(agentId: String) = agentStorage.targetMap[agentId]?.agent

    operator fun get(agentId: String) = agentStorage.targetMap[agentId]?.agent

    fun full(agentId: String): AgentEntry? = agentStorage.targetMap[agentId]

    fun getAllAgents() = agentStorage.targetMap.values

    fun getAllInstalledPluginBeanIds(agentId: String) = getOrNull(agentId)?.plugins?.map { it.id }

    suspend fun AgentInfo.addPlugins(plugins: List<String>) {
        val agentEntry = full(id)!!
        plugins.forEach { pluginId ->
            val dp: Plugin = this@AgentManager.plugins[pluginId]!!
            ensurePluginInstance(agentEntry, dp)
        }
        addPluginsToAgent(this, plugins)
    }

    private fun addPluginsToAgent(agentInfo: AgentInfo, plugins: List<String>) {
        plugins.forEach { pluginId ->
            logger.debug { "Add plugin with id $pluginId from lib for agent with id ${agentInfo.id}" }
            this.plugins[pluginId]?.pluginBean?.let { plugin ->
                val existingPluginBeanDb = agentInfo.plugins.find { it.id == pluginId }
                if (existingPluginBeanDb == null) {
                    agentInfo.plugins += plugin
                    logger.info { "Plugin $pluginId successfully added to agent with id ${agentInfo.id}!" }
                }
            }
        }
    }


    private suspend fun wrapBusy(
        ai: AgentInfo,
        block: suspend AgentInfo.() -> Unit
    ) {
        logger.debug { "Agent with id ${ai.name} set busy status" }
        ai.status = AgentStatus.BUSY
        ai.commitChanges()

        try {
            block(ai)
        } finally {
            ai.status = AgentStatus.ONLINE
            logger.debug { "Agent with id ${ai.name} set online status" }
            ai.commitChanges()
        }
    }

    internal fun adminData(agentId: String): AgentData = adminDataVault.getOrPut(agentId) {
        AgentData(
            agentId,
            store.agentStore(agentId),
            app.drillDefaultPackages
        )
    }

    private suspend fun disableAllPlugins(agentId: String) {
        logger.debug { "Reset all plugins for agent with id $agentId" }
        getAllInstalledPluginBeanIds(agentId)?.forEach { pluginId ->
            agentSessions(agentId).applyEach {
                sendToTopic<Communication.Plugin.ToggleEvent>(TogglePayload(pluginId, false))
            }
        }
        logger.debug { "All plugins for agent with id $agentId were disabled" }
    }

    private suspend fun AgentWsSession.enableAllPlugins(agentId: String) {
        logger.debug { "Enabling all plugins for agent with id $agentId" }
        getAllInstalledPluginBeanIds(agentId)?.let { pluginIds ->
            pluginIds.map { pluginId ->
                logger.debug { "Enabling plugin $pluginId for agent $agentId..." }
                sendToTopic<Communication.Plugin.ToggleEvent>(TogglePayload(pluginId, true)) {
                    logger.debug { "Enabled plugin $pluginId for agent $agentId" }
                }
            }.forEach { it.await() }
        }
        logger.debug { "All plugins for agent with id $agentId were enabled" }
    }

    private suspend fun AgentInfo.persistToDatabase() {
        store.agentStore(this.id).store(this)
        agentStorage.targetMap[this.id]?.agent = this
    }

    private suspend fun AgentWsSession.configurePackages(prefixes: List<String>) {
        setPackagesPrefixes(PackagesPrefixes(prefixes))
    }

    suspend fun AgentInfo.sync(needSync: Boolean) {
        logger.debug { "Agent with id $name starting sync, needSync is $needSync" }
        if (status != AgentStatus.NOT_REGISTERED) {
            if (needSync)
                wrapBusy(this) {
                    val info = this
                    val duration = measureTime {
                        agentSessions(id).applyEach {
                            val settings = adminData(id).settings
                            configurePackages(settings.packages)
                            sendPlugins(info)
                            updateSessionHeader(settings.sessionIdHeaderName)
                            triggerClassesSending()
                            enableAllPlugins(id)
                        }
                    }
                    logger.info { "Agent sync took: $duration" }
                    topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(id))
                }
            logger.debug { "Agent with id $name sync was finished" }
        } else {
            logger.warn { "Agent status is not registered" }
        }
    }

    private suspend fun AgentWsSession.updateSessionHeader(sessionIdHeaderName: String) {
        sendToTopic<Communication.Agent.ChangeHeaderNameEvent>(sessionIdHeaderName.toLowerCase())
    }

    suspend fun updateSystemSettings(agentId: String, settings: SystemSettingsDto) {
        val adminData = adminData(agentId)
        getOrNull(agentId)?.let {
            wrapBusy(it) {
                agentSessions(agentId).applyEach {
                    adminData.updateSettings(settings) { oldSettings ->
                        if (oldSettings.sessionIdHeaderName != settings.sessionIdHeaderName) {
                            updateSessionHeader(settings.sessionIdHeaderName)
                        }
                        if (oldSettings.packages != settings.packages) {
                            disableAllPlugins(agentId)
                            configurePackages(settings.packages)
                            triggerClassesSending()
                            full(agentId)?.applyPackagesChanges()
                            enableAllPlugins(id)
                        }
                    }
                }
            }
        }
    }

    suspend fun updateSystemSettings(
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
        info.plugins.forEach { pb ->
            logger.debug { "Sending plugin ${pb.id} to agent ${info.id}" }
            val data = if (info.agentType == AgentType.JAVA) {
                this@AgentManager.plugins[pb.id]?.agentPluginPart!!.readBytes()
            } else byteArrayOf()
            pb.checkSum = hex(sha1(data))
            async("/agent/plugin/${pb.id}/loaded") { //TODO move to the api
                sendToTopic<Communication.Agent.PluginLoadEvent>(PluginBinary(pb, data)).await()
            }.await()
            logger.debug { "Sent plugin ${pb.id} for agent ${info.id}" }
        }
        logger.debug { "Sent plugins for agent ${info.id}" }
    }

    fun serviceGroup(serviceGroupId: String): List<AgentEntry> = getAllAgents().filter {
        it.agent.serviceGroup == serviceGroupId
    }

    suspend fun sendPluginsToAgent(agentInfo: AgentInfo) {
        wrapBusy(agentInfo) {
            agentSessions(id).applyEach { sendPlugins(this@wrapBusy) }
        }
    }

    suspend fun ensurePluginInstance(
        agentEntry: AgentEntry,
        plugin: Plugin
    ): AdminPluginPart<*> {
        return agentEntry.get(plugin.pluginBean.id) {
            val adminPluginData = adminData(agent.id)
            val store = store.agentStore(agent.id)
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

    suspend fun AgentInfo.commitChanges() {
        persistToDatabase()
        notifyAllAgents()
        notifySingleAgent(this.id)
    }
}

suspend fun AgentWsSession.setPackagesPrefixes(data: PackagesPrefixes) =
    sendToTopic<Communication.Agent.SetPackagePrefixesEvent>(data).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic<Communication.Agent.LoadClassesDataEvent>().await()
