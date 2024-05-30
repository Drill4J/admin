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

import com.epam.drill.admin.metrics.repository.MetricsRepository
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
        val groupId: String,
        val appId: String,

        val instanceId: String? = null,
        val commitSha: String? = null,
        val buildVersion: String? = null,

        val baselineInstanceId: String? = null,
        val baselineCommitSha: String? = null,
        val baselineBuildVersion: String? = null,
    )

    @Resource("/risks")
    class Risks(
        val groupId: String,
        val appId: String,
        val currentBranch: String,
        val currentVcsRef: String,
        val baseBranch: String,
        val baseVcsRef: String,
    )

    @Resource("/coverage")
    class Coverage(
        val groupId: String,
        val appId: String,
        val currentVcsRef: String,
    )

    @Resource("/summary")
    class Summary(
        val groupId: String,
        val appId: String,
        val currentBranch: String,
        val currentVcsRef: String,
        val baseBranch: String,
        val baseVcsRef: String,
    )
}

fun Route.metricRoutes() {
    getRisks()
    getCoverage()
    getSummary()
    getBuildDiffReport()
}

fun Route.getBuildDiffReport() {
    val metricsRepository by closestDI().instance<MetricsRepository>()

    get<Metrics.BuildDiffReport> { params ->
        val report = metricsRepository.getBuildDiffReport(
            params.groupId,
            params.appId,
            params.instanceId,
            params.commitSha,
            params.buildVersion,
            params.baselineInstanceId,
            params.baselineCommitSha,
            params.baselineBuildVersion,
        )
        this.call.respond(HttpStatusCode.OK, report)
    }
}

fun Route.getRisks() {
    val metricsRepository by closestDI().instance<MetricsRepository>()

    get<Metrics.Risks> { params ->
        val risks = metricsRepository.getRisksByBranchDiff(
            params.groupId,
            params.appId,
            params.currentBranch,
            params.currentVcsRef,
            params.baseBranch,
            params.baseVcsRef
        )
        this.call.respond(HttpStatusCode.OK, risks)
    }
}

fun Route.getCoverage() {
    val metricsRepository by closestDI().instance<MetricsRepository>()

    get<Metrics.Coverage> { params ->
        val coverage = metricsRepository.getTotalCoverage(
            params.groupId,
            params.appId,
            params.currentVcsRef
        )
        this.call.respond(HttpStatusCode.OK, coverage)
    }
}

fun Route.getSummary() {
    val metricsRepository by closestDI().instance<MetricsRepository>()

    get<Metrics.Summary> { params ->
        val summary = metricsRepository.getSummaryByBranchDiff(
            params.groupId,
            params.appId,
            params.currentBranch,
            params.currentVcsRef,
            params.baseBranch,
            params.baseVcsRef
        )
        this.call.respond(HttpStatusCode.OK, summary)
    }
}