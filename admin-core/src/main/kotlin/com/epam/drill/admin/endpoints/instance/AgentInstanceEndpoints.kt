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
import com.epam.drill.admin.auth.config.withRole
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.config.drillConfig
import com.epam.drill.admin.endpoints.AgentManager
import com.epam.drill.admin.endpoints.plugin.PluginDispatcher
import com.epam.drill.common.agent.configuration.AgentMetadata
import com.epam.drill.plugins.test2code.TEST2CODE_PLUGIN
import com.epam.drill.plugins.test2code.api.AddSessionData
import com.epam.drill.plugins.test2code.api.AddTestsPayload
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.common.transport.*
import com.epam.drill.plugins.test2code.multibranch.repository.RawDataRepositoryImpl
import com.epam.drill.plugins.test2code.sendPostRequest
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.locations.post
import io.ktor.locations.put
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

private val logger = KotlinLogging.logger {}
object Paths {
    @Location("/api/groups/{groupId}/agents/{agentId}/builds/{buildVersion}/instances/{instanceId}")
    data class Instance(val groupId: String, val agentId: String, val buildVersion: String, val instanceId: String)

    @Location("/api/groups/{groupId}/agents/{agentId}/builds/{buildVersion}/instances/{instanceId}/coverage")
    data class Coverage(val groupId: String, val agentId: String, val buildVersion: String, val instanceId: String)

    @Location("/api/groups/{groupId}/agents/{agentId}/builds/{buildVersion}/instances/{instanceId}/class-metadata")
    data class ClassMetadata(val groupId: String, val agentId: String, val buildVersion: String, val instanceId: String)

    @Location("/api/groups/{groupId}/agents/{agentId}/builds/{buildVersion}/instances/{instanceId}/class-metadata/complete")
    data class ClassMetadataComplete(val groupId: String, val agentId: String, val buildVersion: String, val instanceId: String)

    @Location("/api/groups/{groupId}/agents/{agentId}/builds/{buildVersion}/raw-javascript-coverage")
    data class RawJavaScriptCoverage(val groupId: String, val agentId: String, val buildVersion: String)

    @Location("/api/groups/{groupId}/tests-metadata")
    data class TestMetadataRoute(val groupId: String)
}


fun Routing.agentInstanceRoutes() {

    val jsCoverageConverterAddress = application.drillConfig.config("test2code")
        .propertyOrNull("jsCoverageConverterAddress")
        ?.getString()
        ?.takeIf { it.isNotBlank() }
        ?: "http://localhost:8092" // TODO think of default

    val app by closestDI().instance<Application>()
    val agentManager by closestDI().instance<AgentManager>()
    val pd by closestDI().instance<PluginDispatcher>()
    authenticate("api-key") {
        withRole(Role.USER, Role.ADMIN) {

            put<Paths.Instance> {
                val agentConfig = call.decompressAndReceive<AgentMetadata>()
                val agentInfo: AgentInfo = withContext(Dispatchers.IO) {
                    agentManager.attach(agentConfig)
                }
                RawDataRepositoryImpl.saveAgentConfig(agentConfig)
                processPluginData(pd, agentInfo, InitInfo())
                call.respond(HttpStatusCode.OK)
            }

            post<Paths.Coverage> { params ->
                handleAgentRequest(agentManager, params.agentId, params.buildVersion) { agentInfo ->
                    val data = call.decompressAndReceive<CoverageData>()
                    processPluginData(pd, agentInfo, data.toCoverDataPart())
                    RawDataRepositoryImpl.saveCoverDataPart(params.instanceId, data)
                }
            }

            post<Paths.ClassMetadata> { params ->
                handleAgentRequest(agentManager, params.agentId, params.buildVersion) { agentInfo ->
                    val data = call.decompressAndReceive<ClassMetadata>()
                    RawDataRepositoryImpl.saveInitDataPart(params.instanceId, data)
                    processPluginData(pd, agentInfo, data.toInitDataPart())
                }
            }

            post<Paths.ClassMetadataComplete> { params ->
                handleAgentRequest(agentManager, params.agentId, params.buildVersion) { agentInfo ->
                    processPluginData(pd, agentInfo, Initialized())
                }
            }

            post<Paths.TestMetadataRoute> { params ->
                // TODO use universal Router.handleResponse function to add header and respond ok
                call.addDrillInternalHeader()

                val data = call.decompressAndReceive<AddTestsPayload>()
                RawDataRepositoryImpl.saveTestMetadata(data)

                call.respond(HttpStatusCode.OK)
            }

            post<Paths.RawJavaScriptCoverage> { params ->
                call.addDrillInternalHeader()
                val (groupId, agentId, buildVersion) = params
                val data = call.decompressAndReceive<AddSessionData>()

                sendPostRequest(
                    "$jsCoverageConverterAddress/groups/${groupId}/agents/${agentId}/builds/${buildVersion}/v8-coverage",
                    data
                )

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}


private suspend fun PipelineContext<Unit, ApplicationCall>.handleAgentRequest(
    agentManager: AgentManager,
    agentId: String,
    buildVersion: String,
    handler: suspend (AgentInfo) -> Any
) {
    call.addDrillInternalHeader()
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
    pd: PluginDispatcher,
    agentInfo: AgentInfo,
    data: T
) {
    pd.processPluginData(agentInfo, TEST2CODE_PLUGIN, data)
}

private fun ApplicationCall.addDrillInternalHeader() = this.response.header("drill-internal", "true")

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
