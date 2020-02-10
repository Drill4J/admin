package com.epam.drill.admin.notification

import com.epam.drill.admin.admindata.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.common.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.*

internal val logger = KotlinLogging.logger {}

class NotificationManager(override val kodein: Kodein) : KodeinAware {
    private val topicResolver: TopicResolver by instance()
    private val plugins: Plugins by instance()
    private val agentManager: AgentManager by instance()
    private val _notifications = atomic(persistentMapOf<String, Notification>())

    val notifications
        get() = _notifications.value

    fun save(notification: Notification) {
        val notificationId = notification.id
        logger.info {
            "New notification with $notificationId associated with agent ${notification.agentId}." +
                    " Message: ${notification.message}"
        }
        _notifications.update { it.put(notificationId, notification) }
    }

    fun readAll() {
        notifications.values.forEach { notification ->
            _notifications.update { it.put(notification.id, notification.copy(read = true)) }
        }
    }

    fun read(id: String): Boolean = notifications[id]?.let { notification ->
        _notifications.update { it.put(id, notification.copy(read = true)) }
        true
    } ?: false

    fun deleteAll() {
        _notifications.update { it.clear() }
    }

    fun delete(id: String): Boolean = notifications[id]?.let {
        _notifications.update { it.remove(id) }
        true
    } ?: false

    suspend fun handleNewBuildNotification(agentInfo: AgentInfo) {
        val buildManager = agentManager.adminData(agentInfo.id).buildManager
        val previousBuildVersion = buildManager[agentInfo.buildVersion]?.parentVersion
        if (!previousBuildVersion.isNullOrEmpty() && previousBuildVersion != agentInfo.buildVersion) {
            saveNewBuildNotification(agentInfo, buildManager, previousBuildVersion)
        }
    }

    private suspend fun saveNewBuildNotification(
        agentInfo: AgentInfo,
        buildManager: AgentBuildManager,
        previousBuildVersion: String
    ) {
        val id = UUID.randomUUID().toString()
        save(
            Notification(
                id,
                agentInfo.id,
                System.currentTimeMillis(),
                NotificationType.BUILD,
                false,
                createNewBuildMessage(buildManager, previousBuildVersion, agentInfo)
            )
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
