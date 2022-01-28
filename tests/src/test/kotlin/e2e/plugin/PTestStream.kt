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
package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.e2e.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.*

class PTestStream : PluginStreams() {

    val processedData = Channel<String?>(Channel.UNLIMITED)
    val packageChangesCount = Channel<String?>(Channel.UNLIMITED)

    private lateinit var iut: SendChannel<Frame>

    @ExperimentalCoroutinesApi
    override fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>, isDebugStream: Boolean) {
        iut = out
        app.launch {
            incoming.consumeEach {
                if (it is Frame.Text) {
                    val parseJson = it.readText().parseJson() as JsonObject
                    val url = (parseJson[WsReceiveMessage::destination.name] as JsonPrimitive).content
                    val messageJson = parseJson[Subscribe::message.name].toString()
                    if (isDebugStream) {
                        println("Plugin << url=$url, content=$messageJson")
                    }
                    when (url) {
                        "/processed-data" -> {
                            processedData.sendMessage(messageJson, isDebugStream)
                        }
                        "/packagesChangesCount" -> {
                            packageChangesCount.sendMessage(messageJson, isDebugStream)
                        }
                        else -> println("!!!!!!Can't process yet")
                    }
                }
            }
        }
    }

    override suspend fun initSubscriptions(subscription: AgentSubscription) {
        subscribe(subscription, "/packagesChangesCount")
        subscribe(subscription, "/processed-data")
        //handle empty messages on subscription
        packageChangesCount.receive()
        processedData.receive()
    }

    override suspend fun subscribe(subscription: AgentSubscription, destination: String) {
        val message = Subscription.serializer() stringify subscription
        iut.send(uiMessage(Subscribe(destination, message)))
    }

    private suspend fun Channel<String?>.sendMessage(
        message: String,
        streamTracing: Boolean
    ) {
        val sentMessage = message.takeIf {
            it.any() && it != "[]" && it != "\"\""
        }
        if (streamTracing) {
            println("Plugin >> message=$sentMessage")
        }
        send(sentMessage)
    }
}
