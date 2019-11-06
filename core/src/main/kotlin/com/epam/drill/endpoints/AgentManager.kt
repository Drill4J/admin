package com.epam.drill.endpoints

import com.epam.drill.admindata.*
import com.epam.drill.agentmanager.*
import com.epam.drill.common.*
import com.epam.drill.dataclasses.*
import com.epam.drill.endpoints.agent.*
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
    private val notificationsManager: NotificationsManager by instance()
    val app: Application by instance()
    val agentStorage: AgentStorage by instance()
    val plugins: Plugins by instance()
    private val adminData: AdminDataVault by instance()
    private val store: StoreManager by instance()

    suspend fun agentConfiguration(agentId: String, pBuildVersion: String): AgentInfo {
        val agentStore = store.agentStore(agentId)
        val existingAgent =
            agentStore.findById<AgentInfo>(agentId)?.apply { updateBuildVersion(pBuildVersion, agentId) }
                ?: return AgentInfo(
                    agentId,
                    agentId,
                    AgentStatus.NOT_REGISTERED,
                    "",
                    "",
                    pBuildVersion,
                    "",
                    buildVersions = mutableSetOf(AgentBuildVersionJson(pBuildVersion, ""))
                )
        logger.debug { "Agent configuration: $existingAgent" }
        return agentStore.store(existingAgent)
    }

    private suspend fun AgentInfo.updateBuildVersion(pBuildVersion: String, agentId: String) {
        logger.debug { "Update build version for agent with id $agentId. Previous build version is $pBuildVersion" }
        if (status != AgentStatus.OFFLINE) {
            logger.debug { "Agent status not busy" }
            val existingBuildVersion = buildVersions.find { it.id == pBuildVersion }
            if (existingBuildVersion == null) {
                logger.debug { "Build version wit id $pBuildVersion not found" }
                notificationsManager.save(
                    agentId,
                    name,
                    NotificationType.BUILD,
                    notificationsManager.buildArrivedMessage(pBuildVersion)
                )
                topicResolver.sendToAllSubscribed("/notifications")
                buildVersions.add(AgentBuildVersionJson(pBuildVersion, ""))
            }
            this.buildVersion = pBuildVersion
            this.buildAlias = existingBuildVersion?.name ?: ""
            logger.debug { "Build version for agent with id $agentId was updated" }
        }
    }


    suspend fun updateAgent(agentId: String, au: AgentInfoWebSocketSingle) {
        logger.debug { "Agent with id $agentId update with agent info :$au" }
        getOrNull(agentId)?.apply {
            name = au.name
            groupName = au.group
            sessionIdHeaderName = au.sessionIdHeaderName
            description = au.description
            buildAlias = au.buildVersions.firstOrNull { it.id == this.buildVersion }?.name ?: ""
            buildVersions.replaceAll(au.buildVersions)
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
                if (this.buildVersion == buildVersion.id) {
                    this.buildAlias = buildVersion.name
                }
                this.buildVersions.removeAll { it.id == buildVersion.id }
                this.buildVersions.add(buildVersion)
                update(this@AgentManager)
            }
            .apply {
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
        val au = AgentInfoWebSocketSingle(
            id = agInfo.id,
            name = "",
            group = "",
            status = AgentStatus.NOT_REGISTERED,
            description = "",
            buildVersion = agInfo.buildVersion,
            buildAlias = INITIAL_BUILD_ALIAS
        )
            .apply { buildVersions.add(AgentBuildVersionJson(agInfo.buildVersion, INITIAL_BUILD_ALIAS)) }
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

    suspend fun addPluginFromLib(agentId: String, pluginId: String) {
        logger.debug { "Add plugin with id $pluginId from lib for agent with id $agentId" }
        val agentInfo = store.agentStore(agentId).findById<AgentInfo>(agentId)

        if (agentInfo != null) {
            plugins[pluginId]?.pluginBean?.let { plugin ->
                val rawPluginNames = agentInfo.plugins.toList()
                val existingPluginBeanDb = rawPluginNames.find { it.id == pluginId }
                if (existingPluginBeanDb == null) {
                    agentInfo.plugins += plugin
                    wrapBusy(agentInfo) {
                        sendPlugins()
                    }
                    logger.info { "Plugin $pluginId successfully added to agent with id $agentId!" }
                }
            }
        } else {
            logger.warn { "Agent with id $agentId not found in your DB." }
        }
    }


    fun wrapBusy(ai: AgentInfo, block: suspend AgentInfo.() -> Unit) = app.launch {
        logger.debug { "Agent with id ${ai.name} set busy status" }
        ai.status = AgentStatus.BUSY
        ai.update(this@AgentManager)

        try {
            block(ai)
        } finally {
            ai.status = AgentStatus.ONLINE
            logger.debug { "Agent with id ${ai.name} set online status" }
            ai.update(this@AgentManager)
        }
    }


    fun adminData(agentId: String) = adminData[agentId] ?: run {
        val newAdminData = AdminPluginData(agentId, store.agentStore(agentId), app.isDevMode())
        adminData[agentId] = newAdminData
        newAdminData
    }

    suspend fun resetAllPlugins(agentId: String) {
        logger.debug { "Reset all plugins for agent with id $agentId" }
        getAllInstalledPluginBeanIds(agentId)?.forEach { pluginId ->
            agentSession(agentId)
                ?.send(
                    PluginId.serializer().agentWsMessage(
                        "/plugins/resetPlugin",
                        PluginId(pluginId)
                    )
                )
        }
        logger.debug { "All plugins for agent with id $agentId was reset" }
    }

    suspend fun AgentInfo.update() {
        store.agentStore(this.id).store(this)
        agentStorage.targetMap[this.id]!!.agent = this
    }

    suspend fun configurePackages(prefixes: PackagesPrefixes, agentId: String) {
        if (prefixes.packagesPrefixes.isNotEmpty()) {
            agentSession(agentId)?.setPackagesPrefixes(prefixes)
        }
        agentSession(agentId)?.triggerClassesSending()
    }

    fun packagesPrefixes(agentId: String) = adminData(agentId).packagesPrefixes

    suspend fun sync(agentInfo: AgentInfo, needSync: Boolean) {
        logger.debug { "Agent with id ${agentInfo.name} starting sync, needSync is $needSync" }
        if (agentInfo.status != AgentStatus.NOT_REGISTERED) {
            if (needSync)
                wrapBusy(agentInfo) {
                    configurePackages(packagesPrefixes(id), id)
                    sendPlugins()
                    topicResolver.sendToAllSubscribed("/$id/builds")
                }
            logger.debug { "Agent with id ${agentInfo.name} sync was finished" }
        } else {
            logger.warn { "Agent status is not registered" }
        }
    }

    private suspend fun AgentInfo.sendPlugins() {
        agentSession(id)?.apply {
            if (plugins.isEmpty()) return@apply
            plugins.forEach { pb ->
                val data = this@AgentManager.plugins[pb.id]?.agentPluginPart!!.readBytes()
                pb.md5Hash = DigestUtils.md5Hex(data)
                sendBinary("/plugins/load", pb, data).await()
            }
        }
    }

    private suspend fun updateBuildDataOnAllPlugins(agentId: String, buildVersion: String) {
        val plugins = full(agentId)?.instance
        plugins?.values?.forEach { it.updateDataOnBuildConfigChange(buildVersion) }
    }

}

suspend fun AgentInfo.update(agentManager: AgentManager) {
    val ai = this
    agentManager.apply { ai.update() }
    agentManager.update()
    agentManager.singleUpdate(this.id)
}

suspend fun AgentWsSession.setPackagesPrefixes(data: PackagesPrefixes) =
    sendToTopic("/agent/set-packages-prefixes", data).await()

suspend fun AgentWsSession.triggerClassesSending() =
    sendToTopic("/agent/load-classes-data", "").await()