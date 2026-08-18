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

import com.epam.drill.admin.etl.service.EtlService
import com.epam.drill.admin.common.config.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post as postWithParams
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import java.time.Instant
import java.time.LocalDate
import kotlin.getValue


@Resource("/refresh")
class Refresh(
    val groupId: String? = null,
    val reset: Boolean = false,
    val fromDay: String? = null,
    val toDay: String? = null,
)

@Resource("/refresh/status")
class DailyStatuses(
    val groupId: String,
    val fromDay: String? = null,
    val toDay: String? = null,
)

@Resource("/refresh/last-processed-timestamp")
class LastProcessedTimestamp(
    val groupId: String,
)

fun Route.etlManagementRoutes() {
    postRefreshMetrics()
    getRefreshStatus()
    getLastProcessedTimestamp()
    getActiveJobs()
    cancelJobs()
}

fun Route.postRefreshMetrics() {
    val etlService by closestDI().instance<EtlService>()

    postWithParams<Refresh> { params ->
        val fromDay = params.fromDay?.let { LocalDate.parse(it) }
        val toDay = params.toDay?.let { LocalDate.parse(it) }
        when {
            params.reset && fromDay == null && toDay == null -> {
                etlService.rerunAllData(groupId = params.groupId)
                call.respond(HttpStatusCode.OK, ApiResponse("Metrics have reset and refreshed successfully"))
            }
            params.reset -> {
                etlService.rerunDateRange(groupId = params.groupId, from = fromDay, to = toDay)
                call.respond(HttpStatusCode.OK, ApiResponse("Metrics have reset and refreshed successfully"))
            }
            else -> {
                etlService.forceRefresh(groupId = params.groupId)
                call.respond(HttpStatusCode.OK, ApiResponse("Metrics refreshed successfully"))
            }
        }
    }
}

fun Route.getRefreshStatus() {
    val etlService by closestDI().instance<EtlService>()
    get<DailyStatuses> { params ->
        val statuses = etlService.getDailyStatuses(
            groupId = params.groupId,
            from = params.fromDay?.let { LocalDate.parse(it) },
            to = params.toDay?.let { LocalDate.parse(it) },
        )
        call.respond(HttpStatusCode.OK, ApiResponse(statuses.associate { it.day.toString() to it.status.name }))
    }
}

fun Route.getLastProcessedTimestamp() {
    val etlService by closestDI().instance<EtlService>()
    get<LastProcessedTimestamp> { params ->
        val timestamp: Instant? = etlService.getLastProcessedTimestamp(groupId = params.groupId)
        call.respond(HttpStatusCode.OK, ApiResponse(mapOf("lastProcessedTimestamp" to timestamp?.toEpochMilli())))
    }
}

fun Route.getActiveJobs() {
    val etlService by closestDI().instance<EtlService>()
    get<Refresh> { params ->
        val jobs = etlService.getActiveJobs(
            groupId = params.groupId,
            from = params.fromDay?.let { LocalDate.parse(it) },
            to = params.toDay?.let { LocalDate.parse(it) })
        call.respond(HttpStatusCode.OK, ApiResponse(jobs))
    }
}

fun Route.cancelJobs() {
    val etlService by closestDI().instance<EtlService>()
    delete<Refresh> { params ->
        val jobs = etlService.cancelJobs(
            groupId = params.groupId,
            from = params.fromDay?.let { LocalDate.parse(it) },
            to = params.toDay?.let { LocalDate.parse(it) })
        call.respond(HttpStatusCode.OK, ApiResponse(jobs))
    }
}