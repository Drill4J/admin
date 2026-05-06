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
package com.epam.drill.admin.etl.route

import com.epam.drill.admin.common.route.ok
import com.epam.drill.admin.etl.service.EtlService
import com.epam.drill.admin.metrics.repository.impl.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.resources.*
import io.ktor.server.resources.post as postWithParams
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import kotlin.getValue


@Resource("/refresh")
class Refresh(
    val groupId: String? = null,
    val reset: Boolean = false
)

@Resource("/refresh-status")
class RefreshStatus(
    val groupId: String
)

fun Route.etlManagementRoutes() {
    postRefreshMetrics()
    getRefreshStatus()
}

fun Route.postRefreshMetrics() {
    val metricsService by closestDI().instance<EtlService>()

    postWithParams<Refresh> { params ->
        metricsService.refresh(params.groupId, params.reset)
        call.ok("Metrics were refreshed.")
    }
}

fun Route.getRefreshStatus() {
    val metricsService by closestDI().instance<EtlService>()

    get<RefreshStatus> { params ->
        val status = metricsService.getRefreshStatus(params.groupId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(status))
    }
}