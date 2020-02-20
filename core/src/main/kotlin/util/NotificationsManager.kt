package com.epam.drill.admin.util

import com.epam.drill.admin.admindata.*
import com.epam.drill.admin.dataclasses.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.common.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.concurrent.*

class NotificationsManager(override val kodein: Kodein) : KodeinAware {
    private val topicResolver: TopicResolver by instance()
    private val plugins: Plugins by instance()
    private val agentManager: AgentManager by instance()

    private val notifications = ConcurrentHashMap<String, Notification>()
    private val logger = KotlinLogging.logger {}

    val allNotifications
        get() = notifications.values

    fun save(agentId: String, agentName: String, type: NotificationType, message: String) {
        val id = java.util.UUID.randomUUID().toString()
        logger.info { "New notification type $type with $id associated with agent $agentId. Message: $message" }
        notifications[id] = Notification(
            id,
            agentId,
            agentName,
            System.currentTimeMillis(),
            NotificationStatus.UNREAD,
            type,
            message
        )
    }

    fun readAll() {
        allNotifications.forEach {
            notifications[it.id] = it.copy(status = NotificationStatus.READ)
        }
    }

    fun read(id: String): Boolean = notifications[id]?.let {
        val readNotification = it.copy(status = NotificationStatus.READ)
        notifications[id] = readNotification
        notifications[id]!!.id == readNotification.id
    } ?: false

    fun deleteAll() {
        notifications.clear()
    }

    fun delete(id: String): Boolean = notifications[id]?.let {
        notifications.remove(id) != null
    } ?: false

    suspend fun handleNewBuildNotification(agentInfo: AgentInfo) {
        val buildManager = agentManager.adminData(agentInfo.id).buildManager
        val previousBuildVersion = buildManager[agentInfo.buildVersion]?.prevBuild
        if (!previousBuildVersion.isNullOrEmpty() && previousBuildVersion != agentInfo.buildVersion) {
            saveNewBuildNotification(agentInfo, buildManager, previousBuildVersion)
        }
    }

    private suspend fun saveNewBuildNotification(
        agentInfo: AgentInfo,
        buildManager: AgentBuildManager,
        previousBuildVersion: String
    ) {
        save(
            agentInfo.id,
            agentInfo.name,
            NotificationType.BUILD,
            createNewBuildMessage(buildManager, previousBuildVersion, agentInfo)
        )
        topicResolver.sendToAllSubscribed("/notifications")
    }

    private suspend fun createNewBuildMessage(
        buildManager: AgentBuildManager,
        previousBuildVersion: String,
        agentInfo: AgentInfo
    ): String {
        val methodChanges = buildManager[agentInfo.buildVersion]?.methodChanges ?: MethodChanges()
        val buildDiff = BuildDiff(
            methodChanges.map[DiffType.MODIFIED_BODY]?.count() ?: 0,
            methodChanges.map[DiffType.MODIFIED_DESC]?.count() ?: 0,
            methodChanges.map[DiffType.MODIFIED_NAME]?.count() ?: 0,
            methodChanges.map[DiffType.NEW]?.count() ?: 0,
            methodChanges.map[DiffType.DELETED]?.count() ?: 0
        )

        val newBuildArrivedMessage = NewBuildArrivedMessage(
            agentInfo.buildVersion,
            previousBuildVersion,
            buildDiff,
            pluginsRecommendations(agentInfo)
        )
        return NewBuildArrivedMessage.serializer() stringify newBuildArrivedMessage
    }

    private suspend fun pluginsRecommendations(
        agentInfo: AgentInfo
    ): List<String> {
        return agentManager.full(agentInfo.id)?.let { agentEntry ->
            val connectedPlugins = plugins.filter {
                agentInfo.plugins.map { pluginMetadata -> pluginMetadata.id }.contains(it.key)
            }

            //TODO rewrite this double parsing
            val results = connectedPlugins.map { (_, plugin: Plugin) ->
                val pluginInstance = agentManager.ensurePluginInstance(agentEntry, plugin)
                pluginInstance.getPluginData(type = "recommendations") as? String
            }.filterNotNull()
            results.mapNotNull { result ->
                try {
                    String.serializer().list parse result
                } catch (exception: JsonDecodingException) {
                    logger.error(exception) { "Parsing result '$result' finished with exception: " }
                    null
                }
            }.flatten()
        } ?: emptyList()
    }
}
