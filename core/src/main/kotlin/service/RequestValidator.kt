package com.epam.drill.admin.service

import com.epam.drill.common.*
import com.epam.drill.admin.endpoints.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

const val agentIsBusyMessage =
    "Sorry, this agent is busy at the moment. Please try again later"
val srv = AttributeKey<Boolean>("isServiceGroup")
class RequestValidator(override val kodein: Kodein) : KodeinAware {
    val app: Application by instance()
    val am: AgentManager by instance()
    private val logger = KotlinLogging.logger { }

    init {
        app.routing {
            intercept(ApplicationCallPipeline.Call) {
                if (context is RoutingApplicationCall) {
                    val agentId = context.parameters["agentId"]

                    if (agentId != null) {
                        val agentInfo = am.getOrNull(agentId)
                        when {
                            agentInfo?.status == null -> {
                                if (am.getAllAgents().none { it.agent.serviceGroup == agentId }) {
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ValidationResponse("Agent '$agentId' not found")
                                    )
                                    return@intercept finish()
                                }
                                call.attributes.put(srv, true)
                            }
                            agentInfo.status == AgentStatus.BUSY -> {
                                logger.info { "Agent status is busy" }

                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ValidationResponse(agentIsBusyMessage)
                                )
                                return@intercept finish()
                            }


                        }
                    }
                }
            }
        }
    }

}

@Serializable
data class ValidationResponse(val message: String)
