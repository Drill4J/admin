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
import com.epam.drill.admin.metrics.repository.impl.PagedDataResponse
import com.epam.drill.admin.metrics.repository.impl.Paging
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
    @Resource("/applications")
    class Applications(
        val parent: Metrics,

        val groupId: String? = null,
    )

    @Resource("/builds")
    class Builds(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
        val branch: String? = null,
        val envId: String? = null,

        val page: Int? = null,
        val pageSize: Int? = null
    )

    @Resource("/coverage-treemap")
    class CoverageTreemap(
        val parent: Metrics,

        val buildId: String,
        val testTag: String? = null,
        val envId: String? = null,
        val branch: String? = null,
        val packageNamePattern: String? = null,
        val classNamePattern: String? = null,
        val rootId: String? = null
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

    @Resource("/changes")
    class Changes(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
        val instanceId: String? = null,
        val commitSha: String? = null,
        val buildVersion: String? = null,
        val baselineInstanceId: String? = null,
        val baselineCommitSha: String? = null,
        val baselineBuildVersion: String? = null,

        val page: Int? = null,
        val pageSize: Int? = null
    )

    @Resource("/coverage")
    class Coverage(
        val parent: Metrics,

        val buildId: String,
        val testTag: String? = null,
        val envId: String? = null,
        val branch: String? = null,
        val packageNamePattern: String? = null,
        val classNamePattern: String? = null,

        val page: Int? = null,
        val pageSize: Int? = null
    )
}

fun Route.metricsRoutes() {
    getApplications()
    getBuilds()
    getBuildDiffReport()
    getRecommendedTests()
    getCoverageTreemap()
    getChanges()
    getCoverage()
}

fun Route.getApplications() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.Applications> { params ->
        val data = metricsService.getApplications(
            params.groupId,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuilds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.Builds> { params ->
        val data = metricsService.getBuilds(
            params.groupId,
            params.appId,
            params.branch,
            params.envId,
            params.page,
            params.pageSize
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data.items,
                Paging(data.page, data.pageSize, data.total)
            )
        )
    }
}

fun Route.getCoverageTreemap() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.CoverageTreemap> { params ->
        val treemap = metricsService.getCoverageTreemap(
            params.buildId,
            params.testTag,
            params.envId,
            params.branch,
            params.packageNamePattern,
            params.classNamePattern,
            params.rootId
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
            params.coverageThreshold,
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
            baselineBuildVersion = params.baselineBuildVersion,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(report))
    }
}

fun Route.getChanges() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.Changes> { params ->
        val data = metricsService.getChanges(
            groupId = params.groupId,
            appId = params.appId,
            instanceId = params.instanceId,
            commitSha = params.commitSha,
            buildVersion = params.buildVersion,
            baselineInstanceId = params.baselineInstanceId,
            baselineCommitSha = params.baselineCommitSha,
            baselineBuildVersion = params.baselineBuildVersion,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data.items,
                Paging(data.page, data.pageSize, data.total)
            )
        )
    }
}

fun Route.getCoverage() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.Coverage> { params ->
        val data = metricsService.getCoverage(
            buildId = params.buildId,
            testTag = params.testTag,
            envId = params.envId,
            branch = params.branch,
            packageNamePattern = params.packageNamePattern,
            classNamePattern = params.classNamePattern,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data.items,
                Paging(data.page, data.pageSize, data.total)
            )
        )
    }
}
