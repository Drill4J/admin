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

import com.epam.drill.admin.auth.config.withRole
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.config.drillConfig
import com.epam.drill.common.agent.configuration.AgentMetadata
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import mu.KotlinLogging
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

    intercept(ApplicationCallPipeline.Call) {
        call.response.header("drill-internal", "true")
        proceed()
    }

    val jsCoverageConverterAddress = application.drillConfig.config("test2code")
        .propertyOrNull("jsCoverageConverterAddress")
        ?.getString()
        ?.takeIf { it.isNotBlank() }
        ?: "http://localhost:8092" // TODO think of default

    authenticate("api-key") {
        withRole(Role.USER, Role.ADMIN) {
            put<Paths.Instance> {
                handleRequest<AgentMetadata> { data ->
                    RawDataRepositoryImpl.saveAgentConfig(data)
                }
            }

            post<Paths.Coverage> { params ->
                handleRequest<CoverageData> { data ->
                    RawDataRepositoryImpl.saveCoverDataPart(params.instanceId, data)
                }
            }

            post<Paths.ClassMetadata> { params ->
                handleRequest<ClassMetadata> { data ->
                    RawDataRepositoryImpl.saveInitDataPart(params.instanceId, data)
                }
            }

            post<Paths.ClassMetadataComplete> { params ->
                call.respond(HttpStatusCode.OK, "Deprecated")
            }

            post<Paths.TestMetadataRoute> { params ->
                handleRequest<AddTestsPayload> { data ->
                    RawDataRepositoryImpl.saveTestMetadata(data)
                }
           }

            post<Paths.RawJavaScriptCoverage> { params ->
                handleRequest<AddSessionData> { data ->
                    val (groupId, agentId, buildVersion) = params
                    sendPostRequest(
                        "$jsCoverageConverterAddress/groups/${groupId}/agents/${agentId}/builds/${buildVersion}/v8-coverage",
                        data
                    )
                    // send empty response (to avoid returning response from js-converter (js-agent))
                    ""
                }
            }
        }
    }
}

private suspend inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.handleRequest(
    handler: (data: T) -> Any
) {
    val data = call.decompressAndReceive<T>()
    val response = handler(data)
    call.respond(HttpStatusCode.OK, response)
}

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
