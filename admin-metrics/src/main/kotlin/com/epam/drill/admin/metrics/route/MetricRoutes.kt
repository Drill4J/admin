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
package com.epam.drill.admin.metrics.route

import com.epam.drill.admin.metrics.repository.impl.ApiResponse
import com.epam.drill.admin.metrics.service.MetricsService
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Resource("/metrics")
class Metrics {

    @Resource("/build-diff-report")
    class BuildDiffReport(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
        val instanceId: String? = null,
        val commitSha: String? = null,
        val buildVersion: String? = null,
        val baselineInstanceId: String? = null,
        val baselineCommitSha: String? = null,
        val baselineBuildVersion: String? = null,
        val coverageThreshold: Double = 1.0,
    )

}

fun Route.metricRoutes() {
    getBuildDiffReport()
}

fun Route.getBuildDiffReport() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.BuildDiffReport> { params ->
        val report = metricsService.getBuildDiffReport(
            params.groupId,
            params.appId,
            params.instanceId,
            params.commitSha,
            params.buildVersion,
            params.baselineInstanceId,
            params.baselineCommitSha,
            params.baselineBuildVersion,
            params.coverageThreshold
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(report))
    }
}
