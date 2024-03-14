package com.epam.drill.admin.writer.rawdata.route

import com.epam.drill.admin.writer.rawdata.service.RawDataService
import io.ktor.client.features.json.JsonFeature
import com.epam.drill.common.agent.configuration.AgentMetadata
import com.epam.drill.plugins.test2code.api.AddSessionData
import com.epam.drill.plugins.test2code.api.AddTestsPayload
import com.epam.drill.plugins.test2code.common.transport.ClassMetadata
import com.epam.drill.plugins.test2code.common.transport.CoverageData
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
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
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.json.Json

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

fun Route.putAgentConfig() {
    put<Paths.Instance> {
        handleRequest<AgentMetadata> { data ->
            RawDataService.saveAgentConfig(data)
        }
    }
}

fun Route.postCoverage() {
    post<Paths.Coverage> { params ->
        handleRequest<CoverageData> { data ->
            RawDataService.saveCoverDataPart(params.instanceId, data)
        }
    }
}

fun Route.postCLassMetadata() {
    post<Paths.ClassMetadata> { params ->
        handleRequest<ClassMetadata> { data ->
            RawDataService.saveInitDataPart(params.instanceId, data)
        }
    }
}

fun Route.postClassMetadataComplete() {
    post<Paths.ClassMetadataComplete> { params ->
        call.respond(HttpStatusCode.OK, "Deprecated")
    }
}

fun Route.postTestMetadata() {
    post<Paths.TestMetadataRoute> { params ->
        handleRequest<AddTestsPayload> { data ->
            RawDataService.saveTestMetadata(data)
        }
    }
}

fun Route.postRawJavaScriptCoverage(jsCoverageConverterAddress: String) {
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

internal suspend inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.handleRequest(
    handler: (data: T) -> Any
) {
    val data = call.decompressAndReceive<T>()
    val response = handler(data)
    call.respond(HttpStatusCode.OK, response)
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

internal fun <T> deserializeProtobuf(data: ByteArray, serializer: KSerializer<T>): T {
    return ProtoBuf.decodeFromByteArray(serializer, data)
}

internal suspend fun sendPostRequest(url: String, data: Any) {
    val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    client.post<String>(url) {
        header("Content-Type", "application/json")
        body = data
    }
}