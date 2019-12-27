package com.epam.drill.endpoints

import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admindata.*
import com.epam.drill.agentmanager.*
import com.epam.drill.api.*
import com.epam.drill.common.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.*
import com.epam.drill.storage.*
import com.epam.drill.system.*
import com.epam.drill.util.*
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

const val INITIAL_BUILD_ALIAS = "Initial build"

class AgentManager(override val kodein: Kodein) : KodeinAware {

    private val topicResolver: TopicResolver by instance()
    private val store: StoreManager by instance()
    private val wsService: Sender by instance()
    private val serviceGroupManager: ServiceGroupManager by instance()
    val app: Application by instance()
    val agentStorage: AgentStorage by instance()
    val plugins: Plugins by instance()
    val adminDataVault: AdminDataVault by instance()
    private val notificationsManager: NotificationsManager by instance()

    private val _instanceIds = atomic(persistentHashMapOf<String, PersistentSet<String>>())

    suspend fun attach(config: AgentConfig, needSync: Boolean, session: AgentWsSession): AgentInfo {
        val id = config.id
        logger.debug { "Service group id ${config.serviceGroupId}" }
        if (config.serviceGroupId.isNotBlank()) {
            serviceGroupManager.syncOnAttach(config.serviceGroupId)
        }
        val agentStore = store.agentStore(id)
        val existingInfo = agentStore.findById<AgentInfo>(id)?.processBuild(config.buildVersion)
        val info = existingInfo ?: config.toAgentInfo()
        info.addInstanceId(config.instanceId)
        agentStorage.put(id, AgentEntry(info, session))
        adminData(id).loadStoredData()
        app.launch {
            existingInfo?.sync(needSync) // sync only existing info!
        }
        info.persistToDatabase()
        session.updateConfig(info)
        return info
    }

    val AgentInfo.instanceIds: PersistentSet<String>
        get() = instanceIds(_instanceIds.value)

    private fun AgentInfo.addInstanceId(instanceId: String) {
        persistentSetOf<String>()
        _instanceIds.update { it.put(id, instanceIds(it) + instanceId) }
    }

    suspend fun AgentInfo.removeInstance(instanceId: String) {
        _instanceIds.update { it.put(id, instanceIds(it) - instanceId) }
        if (instanceIds.isEmpty()) {
            remove(this)
            logger.info { "Agent with id '${id}' was disconnected" }
        } else {
            notifySingleAgent(id)
            logger.info { "Instance '$instanceId' of Agent '${id}' was disconnected" }
        }
    }

    private fun AgentInfo.instanceIds(it: PersistentMap<String, PersistentSet<String>>) =
        it[id].orEmpty().toPersistentSet()

    private fun AgentInfo.processBuild(version: String) = apply {
        logger.debug { "Updating build version for agent with id $id. Build version is $version" }
        if (status != AgentStatus.OFFLINE) {
            val buildInfo = adminData(id).buildManager[version]
            this.buildVersion = version
            this.buildAlias = buildInfo?.buildAlias ?: ""
            logger.debug { "Build version for agent with id $id was updated" }
        }
    }

    suspend fun applyPackagesChangesOnAllPlugins(agentId: String) {
        val agentEntry = full(agentId)
        val plugins = agentEntry?.agent?.plugins?.map { it.id }
        plugins?.forEach { pluginId -> agentEntry.instance[pluginId]?.applyPackagesChanges() }
    }


    suspend fun updateAgent(agentId: String, au: AgentInfoWebSocket) {
        logger.debug { "Agent with id $agentId update with agent info :$au" }
        getOrNull(agentId)?.apply {
            name = au.name
            groupName = au.group
            sessionIdHeaderName = au.sessionIdHeaderName
            description = au.description
            buildAlias = au.buildAlias
            status = au.status
            commitChanges()
        }
        logger.debug { "Agent with id $agentId updated" }
        topicResolver.sendToAllSubscribed("/$agentId/builds")
    }

    suspend fun updateAgentBuildAliases(
        agentId: String,
        buildVersion: AgentBuildVersionJson
    ): AgentInfo? {
        logger.debug { "Update agent build aliases for agent with id $agentId" }
        val result = getOrNull(agentId)
            ?.apply {
                logger.debug { "Update agent build aliases for agent with id $agentId" }
                if (this.buildVersion == buildVersion.id) {
                    this.buildAlias = buildVersion.name
                }
                adminData(agentId).buildManager.renameBuild(buildVersion)
                commitChanges()
                topicResolver.sendToAllSubscribed("/$agentId/builds")
                updateBuildDataOnAllPlugins(agentId, buildVersion.id)
            }
        logger.debug { "AgentInfo updated agent with id $agentId is $result" }
        return result
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
        val au = AgentInfoWebSocket(
            id = agInfo.id,
            name = "",
            group = "",
            status = AgentStatus.NOT_REGISTERED,
            description = "",
            buildVersion = agInfo.buildVersion,
            buildAlias = INITIAL_BUILD_ALIAS,
            packagesPrefixes = adminData(agInfo.id).packagesPrefixes,
            agentType = agInfo.agentType.notation,
            instanceIds = agInfo.instanceIds
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

    suspend fun remove(agentInfo: AgentInfo) {
        agentStorage.remove(agentInfo.id)
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
        val agentEntry = full(agentInfo.id)
        plugins.forEach { pluginId ->
            val dp: Plugin = this.plugins[pluginId]!!
            val pluginClass = dp.pluginClass
            instantiateAdminPluginPart(agentEntry, pluginClass, pluginId)
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


    suspend fun wrapBusy(ai: AgentInfo, block: suspend AgentInfo.() -> Unit) {
        logger.debug { "Agent with id ${ai.name} set busy status" }
        ai.status = AgentStatus.BUSY
        ai.commitChanges()

        try {
            block(ai)
        } finally {
            ai.status = AgentStatus.ONLINE
            logger.debug { "Agent with id ${ai.name} set online status" }
            ai.commitChanges()
            notificationsManager.newBuildNotify(ai)
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
                    updateConfig(this)
                    configurePackages(packagesPrefixes(id), id)//thread sleep
                    sendPlugins()
                    topicResolver.sendToAllSubscribed("/$id/builds")
                }
            logger.debug { "Agent with id $name sync was finished" }
        } else {
            logger.warn { "Agent status is not registered" }
        }
    }

    private suspend fun AgentWsSession.updateConfig(info: AgentInfo) {
        sendToTopic<Communication.Agent.ChangeHeaderNameEvent>(info.sessionIdHeaderName.toLowerCase())
    }

    suspend fun updateConfig(info: AgentInfo) {
        agentSession(info.id)?.updateConfig(info)
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
        val plugins = full(agentId)?.instance
        plugins?.values?.forEach { it.updateDataOnBuildConfigChange(buildVersion) }
    }

    suspend fun instantiateAdminPluginPart(
        agentEntry: AgentEntry?,
        pluginClass: Class<AdminPluginPart<*>>,
        pluginId: String
    ): AdminPluginPart<*> {
        return agentEntry?.instance!![pluginId] ?: run {
            val constructor =
                pluginClass.getConstructor(
                    AdminData::class.java,
                    Sender::class.java,
                    StoreClient::class.java,
                    AgentInfo::class.java,
                    String::class.java
                )
            val agentInfo = agentEntry.agent
            val plugin = constructor.newInstance(
                adminData(agentInfo.id),
                wsService,
                store.agentStore(agentInfo.id),
                agentInfo,
                pluginId
            )
            plugin.initialize()
            agentEntry.instance[pluginId] = plugin
            plugin
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
    groupName = "",
    description = "",
    buildVersion = buildVersion,
    buildAlias = "",
    agentType = agentType
)

suspend fun AgentWsSession.setPackagesPrefixes(data: PackagesPrefixes) =
    sendToTopic<Communication.Agent.SetPackagePrefixesEvent>(data).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic<Communication.Agent.LoadClassesDataEvent>().await()
