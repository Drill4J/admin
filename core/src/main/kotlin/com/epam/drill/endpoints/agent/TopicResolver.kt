@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.endpoints.agent

import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import io.ktor.application.*
import org.kodein.di.*
import org.kodein.di.generic.*

class TopicResolver(override val kodein: Kodein) : KodeinAware {
    private val app: Application by instance()
    private val wsTopic: WsTopic by instance()
    private val sessionStorage: SessionStorage by instance()

    suspend fun sendToAllSubscribed(rout: Any) {
        sendToAllSubscribed(app.toLocation(rout))
    }

    suspend fun sendToAllSubscribed(destination: String) {
        val message = wsTopic.resolve(destination)
        sessionStorage.sendTo(
            destination,
            message,
            WsMessageType.MESSAGE
        )
    }

}
