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
package com.epam.drill.admin.service

import com.epam.drill.admin.endpoints.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.*
import mu.*
import org.kodein.di.*
import org.kodein.di.ktor.closestDI

fun Routing.requestValidatorRoutes() {
    val logger = KotlinLogging.logger { }
    val agentManager by closestDI().instance<AgentManager>()

    intercept(ApplicationCallPipeline.Call) {
        if (context is RoutingApplicationCall) {
            val agentId = context.parameters["agentId"]
            if (agentId != null) {
                val agentInfo = agentManager.getOrNull(agentId)
                if (agentInfo == null &&
                    agentManager.allEntries().none { it.info.groupId == agentId }
                ) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ValidationResponse("Agent '$agentId' not found")
                    )
                    return@intercept finish()
                }
            }
        }
    }
}

@Serializable
data class ValidationResponse(val message: String)
