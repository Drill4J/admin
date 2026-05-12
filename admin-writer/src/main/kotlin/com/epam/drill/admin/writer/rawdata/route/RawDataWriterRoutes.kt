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

import com.epam.drill.admin.common.principal.User
import com.epam.drill.admin.common.route.ok
import com.epam.drill.admin.writer.rawdata.service.RawDataQueuedWriter
import com.epam.drill.admin.writer.rawdata.service.DataManagementService
import com.epam.drill.admin.writer.rawdata.service.RawDataWriter
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.getValue

private val logger = KotlinLogging.logger {}

@Resource("builds")
class BuildsRoute()

@Resource("builds/info")
class BuildsInfoRoute()

@Resource("instances")
class InstancesRoute()

@Resource("coverage")
class CoverageRoute()

@Resource("methods")
class MethodsRoute()

@Resource("tests-metadata")
class TestMetadataRoute()

@Resource("sessions")
class TestSessionRoute()

@Resource("test-definitions")
class TestDefinitionsRoute()

@Resource("test-launches")
class TestLaunchesRoute()

@Resource("method-ignore-rules")
class MethodIgnoreRulesRoute() {
    @Resource("/{id}")
    class Id(val parent: MethodIgnoreRulesRoute, val id: Int)
}
//@Resource("/groups/{groupId}/agents/{appId}/builds/{buildVersion}/raw-javascript-coverage")
//class RawJavaScriptCoverage(val groupId: String, val appId: String, val buildVersion: String)

fun Route.dataIngestRoutes() {
    route("/data-ingest") {
        putBuilds()
        putBuildsInfo()
        putInstances()
        postCoverage()
        putMethods()
        postTestMetadata()
        putTestSessions()
        postTestDefinitions()
        postTestLaunches()
        postMethodIgnoreRules()
        getMethodIgnoreRules()
        deleteMethodIgnoreRule()
//        postRawJavaScriptCoverage(jsCoverageConverterAddress)
    }
}

fun Route.putBuilds() {
    val rawDataQueuedWriter by closestDI().instance<RawDataQueuedWriter>()

    put<BuildsRoute> {
        rawDataQueuedWriter.enqueueBuild(call.decompress())
        call.ok("Build saved")
    }
}

fun Route.putBuildsInfo() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<BuildsInfoRoute> {
        rawDataWriter.saveBuildInfo(call.decompressAndReceive())
        call.ok("Build info saved")
    }
}

fun Route.putInstances() {
    val rawDataQueuedWriter by closestDI().instance<RawDataQueuedWriter>()

    put<InstancesRoute> {
        rawDataQueuedWriter.enqueueInstance(call.decompress())
        call.ok("Instance saved")
    }
}

fun Route.postCoverage() {
    val rawDataQueuedWriter by closestDI().instance<RawDataQueuedWriter>()

    post<CoverageRoute> {
        rawDataQueuedWriter.enqueueCoverage(call.decompress())
        call.ok("Coverage saved")
    }
}

fun Route.putMethods() {
    val rawDataQueuedWriter by closestDI().instance<RawDataQueuedWriter>()

    put<MethodsRoute> {
        rawDataQueuedWriter.enqueueMethods(call.decompress())
        call.ok("Methods saved")
    }
}

fun Route.postTestMetadata() {
    val rawDataQueuedWriter by closestDI().instance<RawDataQueuedWriter>()

    post<TestMetadataRoute> {
        rawDataQueuedWriter.enqueueTestMetadata(call.decompress())
        call.ok("Test metadata saved")
    }
}

fun Route.putTestSessions() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<TestSessionRoute> {
        rawDataWriter.saveTestSession(call.decompressAndReceive(), call.principal<User>())
        call.ok("Test sessions saved")
    }
}

fun Route.postTestDefinitions() {
    val rawDataQueuedWriter by closestDI().instance<RawDataQueuedWriter>()

    post<TestDefinitionsRoute> {
        rawDataQueuedWriter.enqueueTestDefinitions(call.decompress())
        call.ok("Test definitions saved")
    }
}

fun Route.postTestLaunches() {
    val rawDataQueuedWriter by closestDI().instance<RawDataQueuedWriter>()

    post<TestLaunchesRoute> {
        rawDataQueuedWriter.enqueueTestLaunches(call.decompress())
        call.ok("Test launches saved")
    }
}

fun Route.postMethodIgnoreRules() {
    val dataManagementService by closestDI().instance<DataManagementService>()

    post<MethodIgnoreRulesRoute> {
        dataManagementService.saveMethodIgnoreRule(call.decompressAndReceive())
        call.ok("Method ignore rule saved")
    }
}

fun Route.getMethodIgnoreRules() {
    val dataManagementService by closestDI().instance<DataManagementService>()

    get<MethodIgnoreRulesRoute> {
        call.ok(dataManagementService.getAllMethodIgnoreRules())
    }
}

fun Route.deleteMethodIgnoreRule() {
    val dataManagementService by closestDI().instance<DataManagementService>()

    delete<MethodIgnoreRulesRoute.Id> { params ->
        val id = params.id
        dataManagementService.deleteMethodIgnoreRuleById(id)
        call.ok("Method ignore rule deleted")
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

internal val jsonConfig = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal suspend inline fun <reified T : Any> ApplicationCall.decompressAndReceive(): T {
    val body: ByteArray = decompress()
    return when (request.headers[HttpHeaders.ContentType]) {
        ContentType.Application.ProtoBuf.toString() -> ProtoBuf.decodeFromByteArray(T::class.serializer(), body)
        ContentType.Application.Json.toString() -> jsonConfig.decodeFromString(T::class.serializer(), String(body))
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

/**
 * TODO: Workaround for decompressing the request body before upgrading to Ktor 3.0.0, where this feature works out of the box
 * https://github.com/ktorio/ktor/issues/3845
 */
internal suspend inline fun ApplicationCall.decompress(): ByteArray {
    return when (request.headers[HttpHeaders.ContentEncoding]) {
        "gzip" -> decompressGZip(receiveStream())
        else -> receive<ByteArray>()
    }
}
