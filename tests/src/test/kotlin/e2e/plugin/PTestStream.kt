package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.common.*
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
                    val parseJson = json.parseJson(it.readText()) as JsonObject
                    val url = parseJson[WsReceiveMessage::destination.name]!!.content
                    val messageJson = parseJson[Subscribe::message.name]!!.toString()
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
