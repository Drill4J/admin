package com.epam.drill.endpoints

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
    private val wsService: Sender by kodein.instance()
    val app: Application by instance()
    val agentStorage: AgentStorage by instance()
    val plugins: Plugins by instance()
    val adminDataVault: AdminDataVault by instance()
    private val notificationsManager: NotificationsManager by instance()

    suspend fun agentConfiguration(config: AgentConfig): AgentInfo {
        val (agentId: String, instanceId: String, pBuildVersion: String, serviceGroup: String, agentType) = config
        val agentStore = store.agentStore(agentId)
        val existingAgent =
            agentStore.findById<AgentInfo>(agentId)?.apply { processBuild(pBuildVersion, agentId) }
                ?: return AgentInfo(
                    agentId,
                    agentId,
                    AgentStatus.NOT_REGISTERED,
                    serviceGroup,
                    "",
                    "",
                    pBuildVersion,
                    "",
                    agentType
                ).apply { instanceIds.add(instanceId) }
        logger.debug { "Agent configuration: $existingAgent" }
        existingAgent.instanceIds.add(instanceId)
        return agentStore.store(existingAgent)
    }

    private fun AgentInfo.processBuild(pBuildVersion: String, agentId: String) {
        logger.debug { "Updating build version for agent with id $agentId. Build version is $pBuildVersion" }
        if (status != AgentStatus.OFFLINE) {
            val buildInfo = adminData(agentId).buildManager[pBuildVersion]
            this.buildVersion = pBuildVersion
            this.buildAlias = buildInfo?.buildAlias ?: ""
            logger.debug { "Build version for agent with id $agentId was updated" }
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
            update(this@AgentManager)
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
                update(this@AgentManager)
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
                    agentInfo.update(this)
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

    suspend fun update() {
        agentStorage.update()
    }

    suspend fun singleUpdate(agentId: String) {
        agentStorage.singleUpdate(agentId)
    }

    suspend fun put(agentInfo: AgentInfo, session: AgentWsSession) {
        agentStorage.put(agentInfo.id, AgentEntry(agentInfo, session))
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


    fun wrapBusy(ai: AgentInfo, block: suspend AgentInfo.() -> Unit) = GlobalScope.launch {
        logger.debug { "Agent with id ${ai.name} set busy status" }
        ai.status = AgentStatus.BUSY
        ai.update(this@AgentManager)

        try {
            block(ai)
        } finally {
            ai.status = AgentStatus.ONLINE
            logger.debug { "Agent with id ${ai.name} set online status" }
            ai.update(this@AgentManager)
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

    suspend fun AgentInfo.update() {
        store.agentStore(this.id).store(this)
        agentStorage.targetMap[this.id]!!.agent = this
    }

    suspend fun configurePackages(prefixes: List<String>, agentId: String) {
        if (prefixes.isNotEmpty()) {
            agentSession(agentId)?.setPackagesPrefixes(PackagesPrefixes(prefixes))
        }
        agentSession(agentId)?.triggerClassesSending()
    }

    fun packagesPrefixes(agentId: String) = adminData(agentId).packagesPrefixes

    suspend fun sync(agentInfo: AgentInfo, needSync: Boolean) {
        logger.debug { "Agent with id ${agentInfo.name} starting sync, needSync is $needSync" }
        if (agentInfo.status != AgentStatus.NOT_REGISTERED) {
            if (needSync)
                wrapBusy(agentInfo) {
                    updateConfig(this)
                    configurePackages(packagesPrefixes(id), id)//thread sleep
                    sendPlugins()
                    topicResolver.sendToAllSubscribed("/$id/builds")
                }
            logger.debug { "Agent with id ${agentInfo.name} sync was finished" }
        } else {
            logger.warn { "Agent status is not registered" }
        }
    }

    suspend fun updateConfig(aInfo: AgentInfo) {
        agentSession(aInfo.id)?.apply {
            sendToTopic<Communication.Agent.ChangeHeaderNameEvent>(aInfo.sessionIdHeaderName.toLowerCase())
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
        }.join()
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
}

suspend fun AgentInfo.update(agentManager: AgentManager) {
    val ai = this
    agentManager.apply { ai.update() }
    agentManager.update()
    agentManager.singleUpdate(this.id)
}

suspend fun AgentWsSession.setPackagesPrefixes(data: PackagesPrefixes) =
    sendToTopic<Communication.Agent.SetPackagePrefixesEvent>(data).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic<Communication.Agent.LoadClassesDataEvent>().await()