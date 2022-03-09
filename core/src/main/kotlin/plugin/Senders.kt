/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.plugin

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.websocket.*
import com.epam.drill.api.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import io.ktor.http.*
import kotlinx.coroutines.*
import mu.*
import org.kodein.di.*
import java.util.*
import kotlin.time.*

class PluginSenders(override val di: DI) : DIAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val buildManager by instance<BuildManager>()
    private val pluginCaches by instance<PluginCaches>()
    private val pluginSessions by instance<PluginSessions>()

    fun sender(pluginId: String): Sender = object : Sender {
        override suspend fun send(context: SendContext, destination: Any, message: Any) {
            val dest = destination as? String ?: app.toLocation(destination).decodeURLPart()
            logger.trace { "send destination $dest for $destination" }
            val subscription = context.toSubscription()
            val messageKey = subscription.toKey(dest)
            val pluginCache = pluginCaches.get(pluginId, subscription, true)

            //TODO EPMDJ-6817 replace with normal event removal.
            if (message == "") {
                logger.trace { "Removed message by key $messageKey" }
                pluginCache[dest] = ""
                pluginStoresDSM(pluginId).let { store ->
                    withContext(Dispatchers.IO) {
                        store.deleteMessage(messageKey)
                    }
                }
            } else {
                logger.trace { "Sending message to $messageKey" }
                pluginStoresDSM(pluginId).let { store ->
                    withContext(Dispatchers.IO) {
                        measureTimedValue {
                            store.storeMessage(messageKey, message)
                        }.let {
                            logger.trace { "Stored message (key=$messageKey, size=${it.value}) in ${it.duration}" }
                        }
                    }
                }
                pluginCache.remove(dest)
            }
            pluginSessions[pluginId].sendTo(
                destination = messageKey,
                messageProvider = { sessionSubscription ->
                    message.postProcess(sessionSubscription).toWsMessageAsString(
                        destination = dest,
                        type = WsMessageType.MESSAGE,
                        to = sessionSubscription
                    )
                }
            )
        }

        override suspend fun sendAgentAction(agentId: String, pluginId: String, message: Any) {
            message.actionSerializerOrNull()?.let { serializer ->
                val actionStr = serializer stringify message
                val agentAction = PluginAction(pluginId, actionStr, "${UUID.randomUUID()}")
                buildManager.agentSessions(agentId).map {
                    //TODO EPMDJ-8233 move to the api
                    it.sendToTopic<Communication.Plugin.DispatchEvent, PluginAction>(
                        agentAction,
                        topicName = "/plugin/action/${agentAction.confirmationKey}",
                        callback = { logger.info { "Action $actionStr was successfully performed on the agent" } }
                    ).await()
                }
            }
        }
    }
}

internal fun SendContext.toSubscription(): Subscription = when (this) {
    is AgentSendContext -> AgentSubscription(agentId, buildVersion, filterId)
    is GroupSendContext -> GroupSubscription(groupId)
    else -> error("Unknown send context $this")
}
