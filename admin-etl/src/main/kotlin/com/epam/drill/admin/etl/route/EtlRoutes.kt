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
import com.epam.drill.admin.common.model.MessageResponse
import com.epam.drill.admin.common.route.error
import com.epam.drill.admin.common.route.ok
import com.epam.drill.admin.etl.EtlJobStatus
import com.epam.drill.admin.etl.model.EtlJobView
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post as postWithParams
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import java.time.Instant
import java.time.LocalDate
import kotlin.getValue


@Resource("/jobs")
class Jobs(
    val groupId: String? = null,
    val fromDay: String? = null,
    val toDay: String? = null,
)

@Resource("/sync")
class Sync(
    val groupId: String? = null,
    val testSessionId: String? = null,
)

@Resource("/reload")
class Reload(
    val groupId: String? = null,
    val testSessionId: String? = null,
    val reset: Boolean = false,
    val fromDay: String? = null,
    val toDay: String? = null,
    val workers: Int? = null,
)

@Resource("/jobs/status")
class DailyStatuses(
    val groupId: String,
    val fromDay: String? = null,
    val toDay: String? = null,
)

@Resource("/jobs/last-processed-timestamp")
class LastProcessedTimestamp(
    val groupId: String,
)

fun Route.etlManagementRoutes() {
    etlManagementReadRoutes()
    etlManagementWriteRoutes()
}

/** Read refresh status and freshness (USER + ADMIN). */
fun Route.etlManagementReadRoutes() {
    getRefreshStatus()
    getLastProcessedTimestamp()
}

/** Trigger refresh / list or cancel jobs (ADMIN). */
fun Route.etlManagementWriteRoutes() {
    postRefreshMetrics()
    getActiveJobs()
    cancelJobs()
}

fun Route.postRefreshMetrics() {
    val etlService by closestDI().instance<EtlService>()

    postWithParams<Sync> { params ->
        when {
            params.groupId != null && params.testSessionId != null -> {
                val results =
                    etlService.loadTestSessionCoverage(groupId = params.groupId, testSessionId = params.testSessionId)
                respondResults(
                    results,
                    "Test session ${params.testSessionId} loaded",
                    "Test session ${params.testSessionId} loaded with errors"
                )
            }

            else -> {
                etlService.forceRefresh(groupId = params.groupId)
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse("Group ${params.groupId} synchronized")
                )
            }
        }
    }

    postWithParams<Reload> { params ->
        val fromDay = params.fromDay?.let { LocalDate.parse(it) }
        val toDay = params.toDay?.let { LocalDate.parse(it) }
        when {
            params.groupId != null && params.testSessionId != null -> {
                val results = etlService.reloadTestSessionCoverage(
                    groupId = params.groupId,
                    testSessionId = params.testSessionId,
                    withDataDeletion = params.reset
                )
                respondResults(
                    results,
                    "Test session ${params.testSessionId} reloaded",
                    "Test session ${params.testSessionId} reloaded with errors"
                )
            }

            else -> {
                val results = etlService.rerunDateRange(
                    groupId = params.groupId, from = fromDay, to = toDay,
                    workers = params.workers,
                    withDataDeletion = params.reset
                )
                respondResults(
                    results,
                    "Group ${params.groupId} reloaded",
                    "Group ${params.groupId} reloaded with errors"
                )
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
        call.ok(statuses.associate { it.day.toString() to it.status.name })
    }
}

fun Route.getLastProcessedTimestamp() {
    val etlService by closestDI().instance<EtlService>()
    get<LastProcessedTimestamp> { params ->
        val timestamp: Instant? = etlService.getLastProcessedTimestamp(groupId = params.groupId)
        call.ok(mapOf("lastProcessedTimestamp" to timestamp?.toEpochMilli()))
    }
}

fun Route.getActiveJobs() {
    val etlService by closestDI().instance<EtlService>()
    get<Jobs> { params ->
        val jobs = etlService.getActiveJobs(
            groupId = params.groupId,
            from = params.fromDay?.let { LocalDate.parse(it) },
            to = params.toDay?.let { LocalDate.parse(it) })
        call.ok(jobs)
    }
}

fun Route.cancelJobs() {
    val etlService by closestDI().instance<EtlService>()
    delete<Jobs> { params ->
        val jobs = etlService.cancelJobs(
            groupId = params.groupId,
            from = params.fromDay?.let { LocalDate.parse(it) },
            to = params.toDay?.let { LocalDate.parse(it) })
        call.ok(jobs)
    }
}

private suspend fun RoutingContext.respondResults(
    results: List<EtlJobView>,
    successMessage: String,
    failureMessage: String,
    alreadyRunningMessage: String = "Job is running. Check the status later.",
) {
    when {
        results.any { it.status == EtlJobStatus.ERROR } -> {
            call.error(results, failureMessage)
        }

        results.any { it.status == EtlJobStatus.RUNNING } -> {
            call.respond(HttpStatusCode.Accepted, MessageResponse(alreadyRunningMessage))
        }

        else -> {
            call.ok(results, successMessage)
        }
    }
}