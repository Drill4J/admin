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
    private val store: StoreManger by instance()

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
        return agentStore.store(existingAgent)
    }

    private suspend fun AgentInfo.updateBuildVersion(pBuildVersion: String, agentId: String) {
        if (status != AgentStatus.OFFLINE) {
            val existingBuildVersion = buildVersions.find { it.id == pBuildVersion }
            if (existingBuildVersion == null) {
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
        }
    }


    suspend fun updateAgent(agentId: String, au: AgentInfoWebSocketSingle) {
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
        topicResolver.sendToAllSubscribed("/$agentId/builds")
    }

    suspend fun updateAgentBuildAliases(
        agentId: String,
        buildVersion: AgentBuildVersionJson
    ): AgentInfo? = getOrNull(agentId)
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

    suspend fun updateAgentPluginConfig(agentId: String, pc: PluginConfig): Boolean =
        getOrNull(agentId)?.let { agentInfo ->
            agentInfo.plugins.find { it.id == pc.id }?.let { plugin ->
                if (plugin.config != pc.data) {
                    plugin.config = pc.data
                    agentInfo.update(this)
                }
            }
        } != null

    suspend fun resetAgent(agInfo: AgentInfo) {
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
        ai.status = AgentStatus.BUSY
        ai.update(this@AgentManager)
        try {
            block(ai)
        } finally {
            ai.status = AgentStatus.ONLINE
            ai.update(this@AgentManager)
        }
    }


    fun adminData(agentId: String) = adminData[agentId] ?: run {
        val newAdminData = AdminPluginData(agentId, app.isDevMode())
        adminData[agentId] = newAdminData
        newAdminData
    }

    suspend fun resetAllPlugins(agentId: String) {
        getAllInstalledPluginBeanIds(agentId)?.forEach { pluginId ->
            agentSession(agentId)
                ?.send(
                    PluginId.serializer().agentWsMessage(
                        "/plugins/resetPlugin",
                        PluginId(pluginId)
                    )
                )
        }
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
        if (agentInfo.status != AgentStatus.NOT_REGISTERED) {
            if (needSync)
                wrapBusy(agentInfo) {
                    configurePackages(packagesPrefixes(id), id)
                    sendPlugins()
                    topicResolver.sendToAllSubscribed("/$id/builds")
                }
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