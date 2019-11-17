package com.epam.drill.e2e.plugin

import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

class PTestStream : PluginStreams() {
    lateinit var iut: SendChannel<Frame>
    @ExperimentalCoroutinesApi
    override fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>, isDebugStream: Boolean) {
        iut = out
        app.launch {
            incoming.consumeEach {
                //                if (it is Frame.Text)

//                    println(it.readText())
            }

        }
    }


    override suspend fun subscribe(sinf: SubscribeInfo) {
        iut.send(UiMessage(WsMessageType.SUBSCRIBE, "new-destination", SubscribeInfo.serializer() stringify sinf))
    }

}