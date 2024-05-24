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

import io.ktor.server.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.locations.*
import io.ktor.server.locations.post
import io.ktor.server.locations.put
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

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
        rawDataWriter.saveBuild(call.receive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.putInstances() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    put<InstancesRoute> {
        rawDataWriter.saveInstance(call.receive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.postCoverage() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<CoverageRoute> {
        rawDataWriter.saveCoverage(call.receive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.putMethods() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<MethodsRoute> {
        rawDataWriter.saveMethods(call.receive())
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.postTestMetadata() {
    val rawDataWriter by closestDI().instance<RawDataWriter>()

    post<TestMetadataRoute> {
        rawDataWriter.saveTestMetadata(call.receive())
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
            json(Json { ignoreUnknownKeys = true })
        }
    }

    client.post(url) {
        contentType(ContentType.Application.Json)
        body = data
    }
}