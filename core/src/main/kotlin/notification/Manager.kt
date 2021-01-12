package com.epam.drill.admin.notification

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugin.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.*

class NotificationManager(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val topicResolver by instance<TopicResolver>()
    private val pluginCache by instance<PluginCaches>()
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

    fun readAll(): Notifications = _notifications.updateAndGet { notifications ->
        notifications.updateAll { it.copy(read = true) }
    }

    fun delete(id: String): Boolean = id in _notifications.getAndUpdate { it.minus(id) }

    fun deleteAll(): Notifications = _notifications.getAndUpdate { Notifications() }

    internal suspend fun saveNewBuildNotification(
        agentInfo: AgentInfo
    ) {
        logger.debug { "agent='${agentInfo.id}': create 'new build arrived' notification" }
        save(
            Notification(
                id = UUID.randomUUID().toString(),
                agentId = agentInfo.id,
                createdAt = System.currentTimeMillis(),
                type = NotificationType.BUILD,
                message = createNewBuildMessage(agentInfo).toJson()
            )
        )
        topicResolver.sendToAllSubscribed(WsNotifications)
    }

    private suspend fun createNewBuildMessage(
        agentInfo: AgentInfo
    ): NewBuildArrivedMessage = pluginCache.getData(
        agentInfo.id,
        agentInfo.buildVersion,
        type = "build"
    ).let { buildInfo ->
        NewBuildArrivedMessage(
            currentId = agentInfo.buildVersion,
            recommendations = recommendations(agentInfo),
            buildInfo = buildInfo.toJson()
        )
    }

    private suspend fun recommendations(agentInfo: AgentInfo): Set<String> = pluginCache.run {
        getData(
            agentInfo.id,
            agentInfo.buildVersion,
            type = "recommendations"
        )?.let { recommendations ->
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

    fun updateAll(updater: (Notification) -> Notification) = Notifications(
        asc = asc.mutate { map ->
            asc.values.forEach { map[it.id] = updater(it) }
        },
        desc = desc.mutate { map ->
            valuesDesc.forEach { map[it.id] = updater(it) }
        }
    )
}
