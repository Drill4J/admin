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
@file:OptIn(InternalSerializationApi::class)

package com.epam.drill.admin.writer.rawdata.route

import com.epam.drill.admin.writer.rawdata.entity.*
import com.epam.drill.admin.writer.rawdata.route.payload.*
import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import com.epam.drill.admin.writer.rawdata.service.RawDataWriter

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
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
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

@Location("/builds")
object BuildsRoute
@Location("/instances")
object InstancesRoute
@Location("/coverage")
object CoverageRoute
@Location("/methods")
object MethodsRoute
@Location("/tests-metadata")
object TestMetadataRoute
//@Location("/groups/{groupId}/agents/{appId}/builds/{buildVersion}/raw-javascript-coverage")
//data class RawJavaScriptCoverage(val groupId: String, val appId: String, val buildVersion: String)

fun Route.putBuilds() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<BuildsRoute> {
        handleRequest<BuildPayload> { data ->
            rawDataWriter.saveBuild(data)
        }
    }
}

fun Route.putInstances() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<InstancesRoute> {
        handleRequest<InstancePayload> { data ->
            rawDataWriter.saveInstance(data)
        }
    }
}

fun Route.postCoverage() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<CoverageRoute> {
        handleRequest<CoveragePayload> { data ->
            rawDataWriter.saveCoverage(data)
        }
    }
}

fun Route.putMethods() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<MethodsRoute> {
        handleRequest<MethodsPayload> { data ->
            rawDataWriter.saveMethods(data)
        }
    }
}

fun Route.postTestMetadata() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<TestMetadataRoute> {
        handleRequest<AddTestsPayload> { data ->
            rawDataWriter.saveTestMetadata(data)
        }
    }
}

//fun Route.postRawJavaScriptCoverage(jsCoverageConverterAddress: String) {
//    post<RawJavaScriptCoverage> { params ->
//        handleRequest<AddSessionData> { data ->
//            val groupId = params.groupId
//            val appId = params.appId
//            val buildVersion = params.buildVersion
//            sendPostRequest(
//                "$jsCoverageConverterAddress/groups/${groupId}/agents/${appId}/builds/${buildVersion}/v8-coverage",
//                data
//            )
//            // send empty response (to avoid returning response from js-converter (js-agent))
//            ""
//        }
//    }
//}

internal suspend inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.handleRequest(
    handler: (data: T) -> Any
) {
    val data = call.decompressAndReceive<T>()
    val response = handler(data)
    call.respond(HttpStatusCode.OK, response)
}

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal suspend inline fun <reified T : Any> ApplicationCall.decompressAndReceive(): T {
    var body = receive<ByteArray>()
    if (request.headers.contains(HttpHeaders.ContentEncoding, "gzip"))
        body = decompressGZip(body)
    return when (request.headers[HttpHeaders.ContentType]) {
        ContentType.Application.ProtoBuf.toString() -> deserializeProtobuf(body, T::class.serializer())
        // TODO fix serialization issue for TestsMetadata and remove ignoreUnknownKeys workaround
        ContentType.Application.Json.toString() -> json.decodeFromString(T::class.serializer(), String(body))

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