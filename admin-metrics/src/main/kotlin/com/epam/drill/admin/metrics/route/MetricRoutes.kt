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
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

private val logger = KotlinLogging.logger {}

@Resource("/metrics")
class Metrics {

    @Resource("/builds")
    class Builds(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
        val branch: String? = null
    )
    @Resource("/coverage-treemap")
    class CoverageTreemap(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
        val buildId: String
    )
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
        val coverageThreshold: Double = 1.0, // TODO Float should be enough
    )

    @Resource("/recommended-tests")
    class RecommendedTests(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
        val testsToSkip: Boolean = false,
        val testTaskId: String? = null,
        val targetInstanceId: String? = null,
        val targetCommitSha: String? = null,
        val targetBuildVersion: String? = null,
        val baselineInstanceId: String? = null,
        val baselineCommitSha: String? = null,
        val baselineBuildVersion: String? = null,
        val coveragePeriodDays: Int? = null,
    )
}

fun Route.metricsRoutes() {
    getBuilds()
    getBuildDiffReport()
    getRecommendedTests()
    getCoverageTreemap()
}

fun Route.getBuilds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.Builds> { params ->
        val report = metricsService.getBuilds(
            params.groupId,
            params.appId,
            params.branch
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(report))
    }
}

fun Route.getCoverageTreemap() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.CoverageTreemap> { params ->
        val treemap = metricsService.getCoverageTreemap(
            params.groupId,
            params.appId,
            params.buildId
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(treemap))
    }
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

fun Route.getRecommendedTests() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.RecommendedTests> { params ->
        val report = metricsService.getRecommendedTests(
            groupId = params.groupId,
            appId = params.appId,
            testsToSkip = params.testsToSkip,
            testTaskId = params.testTaskId,
            coveragePeriodDays = params.coveragePeriodDays,
            targetInstanceId = params.targetInstanceId,
            targetCommitSha = params.targetCommitSha,
            targetBuildVersion = params.targetBuildVersion,
            baselineInstanceId = params.baselineInstanceId,
            baselineCommitSha = params.baselineCommitSha,
            baselineBuildVersion = params.baselineBuildVersion
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(report))
    }
}
