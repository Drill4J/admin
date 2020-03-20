package com.epam.drill.admin.e2e.plugin

import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.*

class PTestStream : PluginStreams() {
    lateinit var iut: SendChannel<Frame>
    val packagesChangesCount = Channel<String?>()
    @ExperimentalCoroutinesApi
    override fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>, isDebugStream: Boolean) {
        iut = out
        app.launch {
            incoming.consumeEach {
                if (it is Frame.Text) {
                    val parseJson = json.parseJson(it.readText()) as JsonObject
                    val url = parseJson[WsReceiveMessage::destination.name]!!.content
                    val content = parseJson[WsReceiveMessage::message.name]!!.toString()
                    when (url) {
                        "/packagesChangesCount" -> {
                            packagesChangesCount.send(content)
                        }
                        else -> println("Can't process yet")
                    }
                }
            }

        }
    }


    override suspend fun subscribe(sinf: Subscriber, destination: String) {
        iut.send(
            UiMessage(
                WsMessageType.SUBSCRIBE,
                destination,
                jsonSubscribers.stringify(Subscriber.serializer(), sinf)
            )
        )
    }

}
