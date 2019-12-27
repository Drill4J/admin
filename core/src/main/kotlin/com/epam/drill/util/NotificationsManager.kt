package com.epam.drill.util

import com.epam.drill.common.*
import com.epam.drill.dataclasses.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.plugins.*
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

    suspend fun newBuildNotify(agentInfo: AgentInfo) {
        val buildManager = agentManager.adminData(agentInfo.id).buildManager
        val previousBuildVersion = buildManager[agentInfo.buildVersion]?.prevBuild

        if (previousBuildVersion.isNullOrEmpty() || previousBuildVersion == agentInfo.buildVersion) {
            return
        }

        val previousBuildAlias = buildManager[previousBuildVersion]?.buildAlias ?: ""
        val methodChanges = buildManager[agentInfo.buildVersion]?.methodChanges ?: MethodChanges()
        val buildDiff = BuildDiff(
            methodChanges.map[DiffType.MODIFIED_BODY]?.count() ?: 0,
            methodChanges.map[DiffType.MODIFIED_DESC]?.count() ?: 0,
            methodChanges.map[DiffType.MODIFIED_NAME]?.count() ?: 0,
            methodChanges.map[DiffType.NEW]?.count() ?: 0,
            methodChanges.map[DiffType.DELETED]?.count() ?: 0
        )

        save(
            agentInfo.id,
            agentInfo.name,
            NotificationType.BUILD,
            NewBuildArrivedMessage.serializer() stringify NewBuildArrivedMessage(
                agentInfo.buildVersion,
                previousBuildVersion,
                previousBuildAlias,
                buildDiff,
                pluginsRecommendations(agentInfo)
            )
        )
        topicResolver.sendToAllSubscribed("/notifications")
    }

    private suspend fun pluginsRecommendations(
        agentInfo: AgentInfo
    ): List<String> {
        val recommendations = mutableListOf<String>()
        val connectedPlugins = plugins.filter {
            agentInfo.plugins.map { pluginMetadata -> pluginMetadata.id }.contains(it.key)
        }

        connectedPlugins.forEach {
            val result = agentManager.instantiateAdminPluginPart(
                agentManager.full(agentInfo.id),
                it.value.pluginClass,
                it.key
            ).getPluginData(mapOf("type" to "recommendations")).toString().ifEmpty { "[]" }

            try {
                recommendations.addAll(String.serializer().list parse result)
            } catch (exception: JsonDecodingException) {
                logger.error(exception) { "Parsing result '$result' finished with exception: " }
            }
        }
        return recommendations
    }
}
