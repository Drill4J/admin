package com.epam.drill.admin.endpoints

import com.epam.drill.admin.admindata.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.storage.*
import com.epam.drill.api.*
import com.epam.drill.api.dto.*
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

private val logger = KotlinLogging.logger {}

class AgentManager(override val kodein: Kodein) : KodeinAware {

    val app by instance<Application>()
    val agentStorage by instance<AgentStorage>()
    val plugins by instance<Plugins>()

    val activeAgents: List<AgentInfo>
        get() = agentStorage.values
            .map { it.agent }
            .filter { instanceIds(it.id).isNotEmpty() }
            .sortedWith(compareBy(AgentInfo::id))

    private val topicResolver by instance<TopicResolver>()
    private val store by instance<StoreManager>()
    private val sender by instance<Sender>()
    private val serviceGroupManager by instance<ServiceGroupManager>()
    private val adminDataVault by instance<AdminDataVault>()
    private val notificationsManager by instance<NotificationManager>()

    private val _instanceIds = atomic(persistentHashMapOf<String, PersistentSet<String>>())

    suspend fun attach(config: AgentConfig, needSync: Boolean, session: AgentWsSession): AgentInfo {
        logger.debug { "Attaching agent: needSync=$needSync, config=$config" }
        val id = config.id
        val serviceGroup = config.serviceGroupId
        logger.debug { "Service group id '$serviceGroup'" }
        if (serviceGroup.isNotBlank()) {
            serviceGroupManager.syncOnAttach(serviceGroup)
        }
        val oldInstanceIds = instanceIds(id)
        addInstanceId(id, config.instanceId)
        val existingEntry = agentStorage.targetMap[id]
        val currentInfo = existingEntry?.agent
        val buildVersion = config.buildVersion
        val adminData = adminData(id)
        //TODO agent instances
        return if (
            (oldInstanceIds.isEmpty() || config.instanceId in oldInstanceIds)
            && currentInfo?.buildVersion == buildVersion && currentInfo.serviceGroup == serviceGroup
        ) {
            //TODO remove duplicated code
            existingEntry.agentSession = session
            notifySingleAgent(id)
            notifyAllAgents()
            app.launch {
                currentInfo.sync(needSync) // sync only existing info!
            }
            currentInfo.persistToDatabase()
            session.updateSessionHeader(currentInfo)
            currentInfo
        } else {
            val store = store.agentStore(id)
            val storedInfo = store.findById<AgentInfo>(id)?.processBuild(buildVersion)
            val info = storedInfo ?: config.toAgentInfo()
            val entry = AgentEntry(info, session)
            agentStorage.put(id, entry)
            adminData.loadStoredData()
            storedInfo?.initPlugins(entry)
            app.launch {
                storedInfo?.sync(needSync) // sync only existing info!
                notificationsManager.handleNewBuildNotification(info)
            }
            info.persistToDatabase()
            session.updateSessionHeader(info)
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

    private fun addInstanceId(agentId: String, instanceId: String) {
        _instanceIds.update { it.put(agentId, instanceIds(agentId, it) + instanceId) }
    }

    suspend fun AgentInfo.removeInstance(instanceId: String) {
        _instanceIds.update { it.put(id, instanceIds(id, it) - instanceId) }
        if (instanceIds(id).isEmpty()) {
            logger.info { "Agent with id '${id}' was disconnected" }
            agentStorage.handleRemove(id)
        } else {
            notifySingleAgent(id)
            logger.info { "Instance '$instanceId' of Agent '${id}' was disconnected" }
        }
    }

    fun instanceIds(
        agentId: String,
        it: PersistentMap<String, PersistentSet<String>> = _instanceIds.value
    ): PersistentSet<String> {
        return it[agentId].orEmpty().toPersistentSet()
    }

    private fun AgentInfo.processBuild(version: String) = apply {
        logger.debug { "Updating build version for agent with id $id. Build version is $version" }
        if (status != AgentStatus.OFFLINE) {
            this.buildVersion = version
            logger.debug { "Build version for agent with id $id was updated" }
        }
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

    fun agentSession(k: String) = agentStorage.targetMap[k]?.agentSession

    fun buildVersionByAgentId(agentId: String) = getOrNull(agentId)?.buildVersion ?: ""

    operator fun contains(k: String) = k in agentStorage.targetMap

    fun getOrNull(agentId: String) = agentStorage.targetMap[agentId]?.agent

    operator fun get(agentId: String) = agentStorage.targetMap[agentId]?.agent

    fun full(agentId: String) = agentStorage.targetMap[agentId]

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

    fun adminData(agentId: String): AdminPluginData = adminDataVault.getOrPut(agentId) {
        AdminPluginData(agentId, store.agentStore(agentId), app.drillDefaultPackages)
    }

    private suspend fun disableAllPlugins(agentId: String) {
        logger.debug { "Reset all plugins for agent with id $agentId" }
        getAllInstalledPluginBeanIds(agentId)?.forEach { pluginId ->
            agentSession(agentId)
                ?.sendToTopic<Communication.Plugin.ToggleEvent>(TogglePayload(pluginId, false))
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
        if (prefixes.isNotEmpty()) {
            setPackagesPrefixes(PackagesPrefixes(prefixes))
        }
    }

    fun packagesPrefixes(agentId: String) = adminData(agentId).packagesPrefixes

    suspend fun AgentInfo.sync(needSync: Boolean) {
        logger.debug { "Agent with id $name starting sync, needSync is $needSync" }
        if (status != AgentStatus.NOT_REGISTERED) {
            if (needSync)
                wrapBusy(this) {
                    val info = this
                    agentSession(id)?.apply {
                        updateSessionHeader(info)
                        configurePackages(packagesPrefixes(id))//thread sleep
                        delay(500L)
                        triggerClassesSending()
                        sendPlugins(info)
                        restoreLoggingConfig(id)
                        enableAllPlugins(id)
                    }
                    topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(id))
                }
            logger.debug { "Agent with id $name sync was finished" }
        } else {
            logger.warn { "Agent status is not registered" }
        }
    }

    private suspend fun AgentWsSession.updateSessionHeader(info: AgentInfo) {
        sendToTopic<Communication.Agent.ChangeHeaderNameEvent>(info.sessionIdHeaderName.toLowerCase())
    }

    suspend fun updateSystemSettings(agentId: String, systemSettings: SystemSettingsDto) {
        val adminData = adminData(agentId)
        getOrNull(agentId)?.let {
            wrapBusy(it) {
                val agentInfo = this
                agentSession(agentId)?.apply {
                    var modified = false
                    if (agentInfo.sessionIdHeaderName != systemSettings.sessionIdHeaderName) {
                        agentInfo.sessionIdHeaderName = systemSettings.sessionIdHeaderName
                        updateSessionHeader(agentInfo)
                        modified = true
                    }
                    if (adminData.packagesPrefixes != systemSettings.packagesPrefixes) {
                        adminData.resetBuilds()
                        adminData.packagesPrefixes = systemSettings.packagesPrefixes
                        disableAllPlugins(agentId)
                        configurePackages(systemSettings.packagesPrefixes)
                        triggerClassesSending()
                        full(agentId)?.applyPackagesChanges()
                        enableAllPlugins(id)
                        modified = true
                    }
                    if (modified) {
                        agentInfo.persistToDatabase()
                    }
                }
            }
        }
    }

    private suspend fun AgentWsSession.sendPlugins(info: AgentInfo) {
        logger.debug { "Sending ${info.plugins.count()} plugins to agent ${info.id}" }
        info.plugins.forEach { pb ->
            logger.debug { "Sending plugin ${pb.id} to agent ${info.id}" }
            val data = this@AgentManager.plugins[pb.id]?.agentPluginPart!!.readBytes()
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
            agentSession(id)?.sendPlugins(this)
        }
    }

    suspend fun ensurePluginInstance(
        agentEntry: AgentEntry,
        plugin: Plugin
    ): AdminPluginPart<*> {
        return agentEntry.get(plugin.pluginBean.id) {
            val adminPluginData = adminData(agent.id)
            val store = store.agentStore(agent.id)
            val pluginInstance = plugin.createInstance(agent, adminPluginData, sender, store)
            pluginInstance.initialize()
            pluginInstance
        }
    }

    suspend fun AgentInfo.commitChanges() {
        persistToDatabase()
        notifyAllAgents()
        notifySingleAgent(this.id)
    }

    suspend fun configLogging(agentId: String, loggingConfig: LoggingConfig) {
        agentSession(agentId)?.apply {
            setLoggingConfig(loggingConfig)
            val agentStore = store.agentStore(agentId)
            agentStore.store(loggingConfig.associateWithAgent(agentId))
        }
    }

    private suspend fun restoreLoggingConfig(agentId: String) {
        agentSession(agentId)?.apply {
            val agentStore = store.agentStore(agentId)
            val loggingConfig = agentStore.findById<AgentLoggingConfig>(agentId)?.config ?: defaultLoggingConfig
            setLoggingConfig(loggingConfig)
        }
    }
}

fun AgentConfig.toAgentInfo() = AgentInfo(
    id = id,
    name = id,
    status = AgentStatus.NOT_REGISTERED,
    serviceGroup = serviceGroupId,
    environment = "",
    description = "",
    agentVersion = agentVersion,
    buildVersion = buildVersion,
    agentType = agentType
)

suspend fun AgentWsSession.setPackagesPrefixes(data: PackagesPrefixes) =
    sendToTopic<Communication.Agent.SetPackagePrefixesEvent>(data).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic<Communication.Agent.LoadClassesDataEvent>().await()

suspend fun AgentWsSession.setLoggingConfig(loggingConfig: LoggingConfig) {
    sendToTopic<Communication.Agent.UpdateLoggingConfigEvent>(loggingConfig).await()
}
