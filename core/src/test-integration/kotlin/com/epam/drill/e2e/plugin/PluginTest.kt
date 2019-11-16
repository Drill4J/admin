package com.epam.drill.e2e.plugin

import com.epam.drill.agentmanager.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.junit.jupiter.api.*

class PluginTest : E2EPluginTest() {

    @RepeatedTest(3)
    fun testE2ePluginAPI() {
        createSimpleAppWithPlugin<PTestStream>(true, true) {
            connectAgent<Build1> { _, _ ->
                println("hi ag1")
            }
            connectAgent<Build1> { _, _ ->
                println("hi ag2")
            }
            connectAgent<Build1> { _, _ ->
                println("hi ag3")
            }
            connectAgent<Build1> { _, _ ->
                println("hi ag4")
            }
            connectAgent<Build1> { _, _ ->
                println("hi ag5")
            }
            uiWatcher { ch ->
                lateinit var message: AgentInfoWebSocket
                do {
                    message = ch.receive().first()
                } while (message.activePluginsCount != 1 || message.status != AgentStatus.ONLINE)


            }
        }

    }


}

class PTestStream : PluginStreams() {
    lateinit var iut: SendChannel<Frame>
    @ExperimentalCoroutinesApi
    override fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>, isDebugStream: Boolean) {
        iut = out
        app.launch {
            incoming.consumeEach {
                if (it is Frame.Text)
                    println(it.readText())
            }

        }
    }


    override suspend fun subscribe(sinf: SubscribeInfo) {
        iut.send(UiMessage(WsMessageType.SUBSCRIBE, "new-destination", SubscribeInfo.serializer() stringify sinf))
    }

}