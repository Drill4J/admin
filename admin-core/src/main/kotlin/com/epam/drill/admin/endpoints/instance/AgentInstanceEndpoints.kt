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
package com.epam.drill.admin.endpoints.instance

import com.epam.drill.admin.agent.AgentInfo
import com.epam.drill.admin.endpoints.AgentManager
import com.epam.drill.admin.endpoints.plugin.PluginDispatcher
import com.epam.drill.common.agent.configuration.AgentMetadata
import com.epam.drill.plugins.test2code.TEST2CODE_PLUGIN
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import com.epam.drill.plugins.test2code.common.transport.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.locations.put
import io.ktor.locations.post
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

private val logger = KotlinLogging.logger {}

@Location("/api/agents")
object Agents {

    @Location("/{agentId}/builds/{buildVersion}/coverage")
    data class Coverage(val agentId: String, val buildVersion: String)

    @Location("/{agentId}/builds/{buildVersion}/class-metadata")
    data class ClassMetadata(val agentId: String, val buildVersion: String)

    @Location("/{agentId}/builds/{buildVersion}/class-metadata/complete")
    data class ClassMetadataComplete(val agentId: String, val buildVersion: String)
}

class AgentInstanceEndpoints(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val pd by instance<PluginDispatcher>()

    init {
        app.routing {
            registerAgentInstanceRoute()
            sendCoverageRoute()
            sendClassMetadataRoute()
            completeSendingClassMetadataRoute()
        }
    }

    private fun Route.registerAgentInstanceRoute() {
        put<Agents> {
            val agentConfig = call.decompressAndReceive<AgentMetadata>()
            val agentInfo: AgentInfo = withContext(Dispatchers.IO) {
                agentManager.attach(agentConfig)
            }
            processPluginData(agentInfo, InitInfo())
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.sendCoverageRoute() {
        post<Agents.Coverage> { params ->
            handleAgentRequest(params.agentId, params.buildVersion) { agentInfo ->
                val data = call.decompressAndReceive<CoverageData>()
                processPluginData(agentInfo, data.toCoverDataPart())
            }
        }
    }

    private fun Route.sendClassMetadataRoute() {
        post<Agents.ClassMetadata> { params ->
            handleAgentRequest(params.agentId, params.buildVersion) { agentInfo ->
                val data = call.decompressAndReceive<ClassMetadata>()
                processPluginData(agentInfo, data.toInitDataPart())
            }
        }
    }

    private fun Route.completeSendingClassMetadataRoute() {
        post<Agents.ClassMetadataComplete> { params ->
            handleAgentRequest(params.agentId, params.buildVersion) { agentInfo ->
                processPluginData(agentInfo, Initialized())
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handleAgentRequest(
        agentId: String,
        buildVersion: String,
        handler: suspend (AgentInfo) -> Any
    ) {
        agentManager.getOrNull(agentId)
            ?.let { agentInfo ->
                when (agentInfo.build.version) {
                    buildVersion -> {
                        val response = handler(agentInfo)
                        call.respond(HttpStatusCode.OK, response)
                    }

                    else -> {
                        logger.error { "The active build version of agent $agentId is ${agentInfo.build.version}, but the request has $buildVersion build version!" }
                        call.respond(HttpStatusCode.Conflict)
                    }
                }
            } ?: call.respond(HttpStatusCode.NotFound).also {
            logger.error { "Agent $agentId is not attached!" }
        }
    }

    private suspend fun <T : CoverMessage> processPluginData(
        agentInfo: AgentInfo,
        data: T
    ) {
        pd.processPluginData(agentInfo, TEST2CODE_PLUGIN, data)
    }
}

private fun ClassMetadata.toInitDataPart() = InitDataPart(
    astEntities = astEntities
)

private fun CoverageData.toCoverDataPart() = CoverDataPart(
    data = execClassData
)

internal fun <T> deserializeProtobuf(data: ByteArray, serializer: KSerializer<T>): T {
    return ProtoBuf.decodeFromByteArray(serializer, data)
}

internal fun decompressGZip(data: ByteArray): ByteArray {
    val inputStream = ByteArrayInputStream(data)
    val outputStream = ByteArrayOutputStream()
    val gzipInputStream = GZIPInputStream(inputStream)

    val buffer = ByteArray(1024)
    var bytesRead = gzipInputStream.read(buffer)
    while (bytesRead > 0) {
        outputStream.write(buffer, 0, bytesRead)
        bytesRead = gzipInputStream.read(buffer)
    }

    gzipInputStream.close()
    return outputStream.toByteArray()
}

internal suspend inline fun <reified T : Any> ApplicationCall.decompressAndReceive(): T {
    var body = receive<ByteArray>()
    if (request.headers.contains(HttpHeaders.ContentEncoding, "gzip"))
        body = decompressGZip(body)
    return when (request.headers[HttpHeaders.ContentType]) {
        ContentType.Application.ProtoBuf.toString() -> deserializeProtobuf(body, T::class.serializer())
        ContentType.Application.Json.toString() -> Json.decodeFromString(T::class.serializer(), String(body))
        else -> throw UnsupportedMediaTypeException(
            ContentType.parse(
                request.headers[HttpHeaders.ContentType] ?: "application/octet-stream"
            )
        )
    }
}
