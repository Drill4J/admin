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
package com.epam.drill.admin.writer.rawdata.route

import com.epam.drill.admin.writer.rawdata.service.RawDataWriter
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.locations.post
import io.ktor.server.locations.put
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import java.io.InputStream
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
        rawDataWriter.saveBuild(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.putInstances() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<InstancesRoute> {
        rawDataWriter.saveInstance(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.postCoverage() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<CoverageRoute> {
        rawDataWriter.saveCoverage(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.putMethods() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<MethodsRoute> {
        rawDataWriter.saveMethods(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.postTestMetadata() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<TestMetadataRoute> {
        rawDataWriter.saveTestMetadata(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
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

internal suspend fun sendPostRequest(url: String, data: Any) {
    val client = HttpClient(Apache) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
    }

    client.post(url) {
        contentType(ContentType.Application.Json)
        body = data
    }
}

internal val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Workaround for decompressing the request body before upgrading to Ktor 3.0.0, where this feature works out of the box
 * https://github.com/ktorio/ktor/issues/3845
 */
internal suspend inline fun <reified T : Any> ApplicationCall.decompressAndReceive(): T {
    val body: ByteArray = when (request.headers[HttpHeaders.ContentEncoding]) {
        "gzip" -> decompressGZip(receiveStream())
        else -> receive<ByteArray>()
    }
    return when (request.headers[HttpHeaders.ContentType]) {
        ContentType.Application.ProtoBuf.toString() -> ProtoBuf.decodeFromByteArray(T::class.serializer(), body)
        ContentType.Application.Json.toString() -> json.decodeFromString(T::class.serializer(), String(body))
        else -> throw request.headers[HttpHeaders.ContentType]?.let {
            UnsupportedMediaTypeException(ContentType.parse(it))
        } ?: BadRequestException("Content-Type header is missing")
    }
}

internal suspend fun decompressGZip(inputStream: InputStream): ByteArray {
    val decompressedBytes = withContext(Dispatchers.IO) {
        GZIPInputStream(inputStream).readBytes()
    }
    return decompressedBytes
}