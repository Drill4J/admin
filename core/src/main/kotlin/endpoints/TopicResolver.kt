/**
 * Copyright 2020 EPAM Systems
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
