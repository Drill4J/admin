package com.epam.drill.admin.endpoints

import com.epam.drill.admin.common.*
import com.epam.drill.admin.websocket.*
import io.ktor.application.*
import org.kodein.di.*
import org.kodein.di.generic.*

class TopicResolver(override val kodein: Kodein) : KodeinAware {
    private val app by instance<Application>()
    private val wsTopic by instance<WsTopic>()
    private val sessionStorage by instance<SessionStorage>()

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
