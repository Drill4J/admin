package com.epam.drill.admin.notification

import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.common.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.*

internal val logger = KotlinLogging.logger {}

class NotificationManager(override val kodein: Kodein) : KodeinAware {

    private val topicResolver by instance<TopicResolver>()
    private val pluginCache by instance<PluginCache>()
    private val agentManager by instance<AgentManager>()
    private val _notifications = atomic(Notifications())

    val notifications
        get() = _notifications.value

    fun save(notification: Notification) {
        val notificationId = notification.id
        logger.info {
            "New notification with $notificationId associated with agent ${notification.agentId}." +
                " Message: ${notification.message}"
        }
        _notifications.update { it + notification }
    }

    fun read(id: String): Boolean = id in _notifications.updateAndGet { notifications ->
        notifications[id]?.run { copy(read = true) }?.let(notifications::replace) ?: notifications
    }

    fun delete(id: String): Boolean = id in _notifications.updateAndGet { it.minus(id) }

    suspend fun handleNewBuildNotification(agentInfo: AgentInfo) {
        val buildManager = agentManager.adminData(agentInfo.id).buildManager
        val previousBuildVersion = buildManager[agentInfo.buildVersion]?.parentVersion
        if (!previousBuildVersion.isNullOrEmpty() && previousBuildVersion != agentInfo.buildVersion) {
            saveNewBuildNotification(agentInfo, previousBuildVersion)
        }
    }

    private suspend fun saveNewBuildNotification(
        agentInfo: AgentInfo,
        previousBuildVersion: String
    ) {
        save(
            Notification(
                id = UUID.randomUUID().toString(),
                agentId = agentInfo.id,
                createdAt = System.currentTimeMillis(),
                type = NotificationType.BUILD,
                message = createNewBuildMessage(previousBuildVersion, agentInfo)
            )
        )
        topicResolver.sendToAllSubscribed(WsNotifications)
    }

    private fun createNewBuildMessage(
        previousBuildVersion: String,
        agentInfo: AgentInfo
    ): NewBuildArrivedMessage = pluginCache.getData(
        agentInfo.id,
        agentInfo.buildVersion,
        type = "build"
    ).let { buildInfo ->
        NewBuildArrivedMessage(
            currentId = agentInfo.buildVersion,
            prevId = previousBuildVersion,
            recommendations = recommendations(agentInfo),
            buildDiff = buildInfo,
            buildInfo = buildInfo
        )
    }

    private fun recommendations(agentInfo: AgentInfo): Set<String> = pluginCache.run {
        getData(
            agentInfo.id,
            agentInfo.buildVersion,
            type = "recommendations"
        ).let { recommendations ->
            (recommendations as? Iterable<*>)?.mapTo(mutableSetOf()) { "$it" }
        } ?: emptySet()
    }
}


class Notifications(
    private val asc: PersistentMap<String, Notification> = persistentMapOf(),
    private val desc: PersistentMap<String, Notification> = persistentMapOf()
) {

    val valuesDesc get() = desc.values

    operator fun get(id: String) = asc[id]

    operator fun contains(id: String): Boolean = id in asc

    fun replace(notification: Notification): Notifications = if (notification.id in asc) {
        Notifications(
            asc = asc.put(notification.id, notification),
            desc = desc.put(notification.id, notification)
        )
    } else this

    operator fun plus(notification: Notification): Notifications = if (notification.id !in asc) {
        Notifications(
            asc = asc.put(notification.id, notification),
            desc = persistentMapOf(notification.id to notification) + desc
        )
    } else this

    operator fun minus(id: String): Notifications = if (id in asc) {
        Notifications(
            asc = asc - id,
            desc = desc - id
        )
    } else this
}
