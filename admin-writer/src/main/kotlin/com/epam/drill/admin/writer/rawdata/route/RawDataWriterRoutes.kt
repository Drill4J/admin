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

import com.epam.drill.admin.writer.rawdata.exception.InvalidMethodIgnoreRule
import com.epam.drill.admin.writer.rawdata.service.RawDataWriter
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.resources.put
import io.ktor.server.resources.post
import io.ktor.server.resources.get
import io.ktor.server.resources.delete
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import java.io.InputStream
import java.util.zip.GZIPInputStream

private val logger = KotlinLogging.logger {}

@Resource("/data-ingest")
class DataIngestRoutes {

    @Resource("builds")
    class BuildsRoute(val parent: DataIngestRoutes = DataIngestRoutes())

    @Resource("instances")
    class InstancesRoute(val parent: DataIngestRoutes = DataIngestRoutes())

    @Resource("coverage")
    class CoverageRoute(val parent: DataIngestRoutes = DataIngestRoutes())

    @Resource("methods")
    class MethodsRoute(val parent: DataIngestRoutes = DataIngestRoutes())

    @Resource("tests-metadata")
    class TestMetadataRoute(val parent: DataIngestRoutes = DataIngestRoutes())

    @Resource("sessions")
    class TestSessionRoute(val parent: DataIngestRoutes = DataIngestRoutes())

    @Resource("method-ignore-rules")
    class MethodIgnoreRulesRoute(var parent: DataIngestRoutes = DataIngestRoutes()) {
        @Resource("/{id}")
        class Id(val parent: MethodIgnoreRulesRoute, val id: Int)
    }
//@Resource("/groups/{groupId}/agents/{appId}/builds/{buildVersion}/raw-javascript-coverage")
//class RawJavaScriptCoverage(val parent: DataIngestRoutes = DataIngestRoutes(), val groupId: String, val appId: String, val buildVersion: String)

}

fun Route.putBuilds() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<DataIngestRoutes.BuildsRoute> {
        rawDataWriter.saveBuild(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.putInstances() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<DataIngestRoutes.InstancesRoute> {
        rawDataWriter.saveInstance(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.postCoverage() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<DataIngestRoutes.CoverageRoute> {
        rawDataWriter.saveCoverage(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.putMethods() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<DataIngestRoutes.MethodsRoute> {
        rawDataWriter.saveMethods(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.postTestMetadata() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<DataIngestRoutes.TestMetadataRoute> {
        rawDataWriter.saveTestMetadata(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.putTestSessions() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<DataIngestRoutes.TestSessionRoute> {
        rawDataWriter.saveTestSession(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.postMethodIgnoreRules() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<DataIngestRoutes.MethodIgnoreRulesRoute> {
        rawDataWriter.saveMethodIgnoreRule(call.decompressAndReceive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.getMethodIgnoreRules() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    get<DataIngestRoutes.MethodIgnoreRulesRoute> {
        call.respond(HttpStatusCode.OK, rawDataWriter.getAllMethodIgnoreRules())
    }
}

fun Route.deleteMethodIgnoreRule() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    delete<DataIngestRoutes.MethodIgnoreRulesRoute.Id> { params ->
        val id = params.id
        rawDataWriter.deleteMethodIgnoreRuleById(id)
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

fun StatusPagesConfig.rawDataStatusPages() {
    exception<MissingFieldException> { call, exception ->
        logger.trace(exception) { "400 MissingFieldException ${exception.message}" }
        call.respond(
            io.ktor.http.HttpStatusCode.BadRequest,
            kotlin.collections.mapOf("errorMessage" to exception.message)
        )
    }
    exception<InvalidMethodIgnoreRule> { call, exception ->
        logger.trace(exception) { "400 InvalidMethodIgnoreRule ${exception.message}" }
        call.respond(
            io.ktor.http.HttpStatusCode.BadRequest,
            kotlin.collections.mapOf("errorMessage" to exception.message)
        )
    }
}
