package com.epam.drill.admin.endpoints

import com.epam.drill.admin.admindata.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.system.*
import com.epam.drill.admin.util.*
import com.epam.drill.api.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import com.epam.kodux.*
import io.ktor.application.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import mu.*
import org.apache.commons.codec.digest.*
import org.kodein.di.*
import org.kodein.di.generic.*

private val logger = KotlinLogging.logger {}

class AgentManager(override val kodein: Kodein) : KodeinAware {

    val app: Application by instance()
    val agentStorage: AgentStorage by instance()
    val plugins: Plugins by instance()

    val activeAgents
        get() = agentStorage.values
            .map { it.agent }
            .filter { instanceIds(it.id).isNotEmpty() }
            .sortedWith(compareBy(AgentInfo::id))

    private val topicResolver: TopicResolver by instance()
    private val store: StoreManager by instance()
    private val sender: Sender by instance()
    private val serviceGroupManager: ServiceGroupManager by instance()
    private val adminDataVault: AdminDataVault by instance()
    private val notificationsManager: NotificationsManager by instance()

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
            adminData(id).loadStoredData()
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

    suspend fun updateAgent(agentId: String, au: AgentInfoDto) {
        logger.debug { "Agent with id $agentId update with agent info :$au" }
        getOrNull(agentId)?.apply {
            name = au.name
            environment = au.environment
            sessionIdHeaderName = au.sessionIdHeaderName
            description = au.description
            status = au.status
            commitChanges()
        }
        logger.debug { "Agent with id $agentId updated" }
        topicResolver.sendToAllSubscribed("/$agentId/builds")
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
        val au = AgentInfoDto(
            id = agInfo.id,
            serviceGroup = agInfo.serviceGroup,
            name = "",
            environment = "",
            status = AgentStatus.NOT_REGISTERED,
            description = "",
            buildVersion = agInfo.buildVersion,
            packagesPrefixes = adminData(agInfo.id).packagesPrefixes,
            agentType = agInfo.agentType.notation,
            instanceIds = instanceIds(agInfo.id)
        )
        updateAgent(agInfo.id, au)
        logger.debug { "Agent with id ${agInfo.name} has been reset" }
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

    suspend fun addPlugins(agentInfo: AgentInfo, plugins: List<String>) {
        val agentEntry = full(agentInfo.id)!!
        plugins.forEach { pluginId ->
            val dp: Plugin = this.plugins[pluginId]!!
            ensurePluginInstance(agentEntry, dp)
        }
        addPluginsToAgent(agentInfo, plugins)
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


    fun adminData(agentId: String) = adminDataVault[agentId] ?: run {
        val newAdminData = AdminPluginData(agentId, store.agentStore(agentId), app.isDevMode())
        adminDataVault[agentId] = newAdminData
        newAdminData
    }

    suspend fun disableAllPlugins(agentId: String) {
        logger.debug { "Reset all plugins for agent with id $agentId" }
        getAllInstalledPluginBeanIds(agentId)?.forEach { pluginId ->
            agentSession(agentId)
                ?.sendToTopic<Communication.Plugin.ToggleEvent>(TogglePayload(pluginId, false))
        }
        logger.debug { "All plugins for agent with id $agentId were disabled" }
    }

    suspend fun enableAllPlugins(agentId: String) {
        logger.debug { "Reset all plugins for agent with id $agentId" }
        getAllInstalledPluginBeanIds(agentId)?.forEach { pluginId ->
            agentSession(agentId)?.sendToTopic<Communication.Plugin.ToggleEvent>(TogglePayload(pluginId, true))
        }
        logger.debug { "All plugins for agent with id $agentId were enabled" }
    }

    private suspend fun AgentInfo.persistToDatabase() {
        store.agentStore(this.id).store(this)
        agentStorage.targetMap[this.id]?.agent = this
    }

    suspend fun configurePackages(prefixes: List<String>, agentId: String) {
        if (prefixes.isNotEmpty()) {
            agentSession(agentId)?.setPackagesPrefixes(PackagesPrefixes(prefixes))
        }
        agentSession(agentId)?.triggerClassesSending()
    }

    fun packagesPrefixes(agentId: String) = adminData(agentId).packagesPrefixes

    suspend fun AgentInfo.sync(needSync: Boolean) {
        logger.debug { "Agent with id $name starting sync, needSync is $needSync" }
        if (status != AgentStatus.NOT_REGISTERED) {
            if (needSync)
                wrapBusy(this) {
                    updateSessionHeader(this)
                    configurePackages(packagesPrefixes(id), id)//thread sleep
                    sendPlugins()
                    topicResolver.sendToAllSubscribed("/$id/builds")
                }
            logger.debug { "Agent with id $name sync was finished" }
        } else {
            logger.warn { "Agent status is not registered" }
        }
    }

    private suspend fun AgentWsSession.updateSessionHeader(info: AgentInfo) {
        sendToTopic<Communication.Agent.ChangeHeaderNameEvent>(info.sessionIdHeaderName.toLowerCase())
    }

    private suspend fun updateSessionHeader(info: AgentInfo) {
        agentSession(info.id)?.updateSessionHeader(info)
    }

    suspend fun updateSystemSettings(agentId: String, systemSettings: SystemSettingsDto) {
        val adminData = adminData(agentId)
        getOrNull(agentId)?.let {
            wrapBusy(it) {
                if (adminData.packagesPrefixes != systemSettings.packagesPrefixes) {
                    adminData.resetBuilds()
                    adminData.packagesPrefixes = systemSettings.packagesPrefixes
                    full(agentId)?.applyPackagesChanges()
                    disableAllPlugins(agentId)
                    configurePackages(systemSettings.packagesPrefixes, agentId)
                }
                it.sessionIdHeaderName = systemSettings.sessionIdHeaderName
                updateSessionHeader(it)
            }
        }
    }

    private suspend fun AgentInfo.sendPlugins() {
        agentSession(id)?.apply {
            if (plugins.isEmpty()) return@apply
            plugins.forEach { pb ->
                val data = this@AgentManager.plugins[pb.id]?.agentPluginPart!!.readBytes()
                pb.md5Hash = DigestUtils.md5Hex(data)
                sendBinary<Communication.Agent.PluginLoadEvent>(pb, data).await()
            }
        }
    }

    fun serviceGroup(serviceGroupId: String) = getAllAgents().filter { it.agent.serviceGroup == serviceGroupId }

    suspend fun sendPluginsToAgent(agentInfo: AgentInfo) {
        wrapBusy(agentInfo) {
            sendPlugins()
        }
    }

    private suspend fun updateBuildDataOnAllPlugins(agentId: String, buildVersion: String) {
        val plugins = full(agentId)?.plugins
        plugins?.forEach { it.updateDataOnBuildConfigChange(buildVersion) }
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
}

fun AgentConfig.toAgentInfo() = AgentInfo(
    id = id,
    name = id,
    status = AgentStatus.NOT_REGISTERED,
    serviceGroup = serviceGroupId,
    environment = "",
    description = "",
    buildVersion = buildVersion,
    buildAlias = "", //TODO remove after changes in API
    agentType = agentType
)

suspend fun AgentWsSession.setPackagesPrefixes(data: PackagesPrefixes) =
    sendToTopic<Communication.Agent.SetPackagePrefixesEvent>(data).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic<Communication.Agent.LoadClassesDataEvent>().await()
