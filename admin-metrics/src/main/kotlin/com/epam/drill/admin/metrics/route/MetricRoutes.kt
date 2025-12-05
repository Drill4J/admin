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

import com.epam.drill.admin.common.model.DataResponse
import com.epam.drill.admin.common.route.ok
import com.epam.drill.admin.metrics.models.BaselineBuild
import com.epam.drill.admin.metrics.models.Build
import com.epam.drill.admin.metrics.models.CoverageCriteria
import com.epam.drill.admin.metrics.models.MethodCriteria
import com.epam.drill.admin.metrics.models.TestCriteria
import com.epam.drill.admin.metrics.repository.impl.ApiResponse
import com.epam.drill.admin.metrics.repository.impl.PagedDataResponse
import com.epam.drill.admin.metrics.repository.impl.Paging
import com.epam.drill.admin.metrics.service.MetricsService
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
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

    @Resource("/changes-coverage-treemap")
    class ChangesCoverageTreemap(
        val parent: Metrics,

        val buildId: String,
        val baselineBuildId: String,
        val testTag: String? = null,
        val envId: String? = null,
        val branch: String? = null,
        val packageNamePattern: String? = null,
        val classNamePattern: String? = null,
        val rootId: String? = null,
        val page: Int? = null,
        val pageSize: Int? = null,
        val includeDeleted: Boolean? = null,
        val includeEqual: Boolean? = null
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
        val baselineBuildBranches: List<String> = emptyList(),
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
        val includeDeleted: Boolean? = null,
        val includeEqual: Boolean? = null,

        val page: Int? = null,
        val pageSize: Int? = null
    )

    @Resource("/coverage")
    class Coverage(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
        val instanceId: String? = null,
        val commitSha: String? = null,
        val buildVersion: String? = null,
        val testTag: String? = null,
        val envId: String? = null,
        val branch: String? = null,
        val packageName: String? = null,
        val className: String? = null,

        val page: Int? = null,
        val pageSize: Int? = null
    )

    @Resource("/impacted-tests")
    class ImpactedTests(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
        val instanceId: String? = null,
        val commitSha: String? = null,
        val buildVersion: String? = null,

        val baselineInstanceId: String? = null,
        val baselineCommitSha: String? = null,
        val baselineBuildVersion: String? = null,

        val packageName: String? = null,
        val className: String? = null,
        val methodName: String? = null,
        @Deprecated("Use packageName instead")
        val packageNamePattern: String? = null,
        @Deprecated("Use className instead")
        val classNamePattern: String? = null,

        val testTaskId: String? = null,
        val testTag: String? = null,
        val testPath: String? = null,
        val testName: String? = null,

        val coverageBranches: List<String> = emptyList(),
        val coverageAppEnvIds: List<String> = emptyList(),

        val page: Int? = null,
        val pageSize: Int? = null,
    )

    @Resource("/impacted-methods")
    class ImpactedMethods(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
        val instanceId: String? = null,
        val commitSha: String? = null,
        val buildVersion: String? = null,

        val baselineInstanceId: String? = null,
        val baselineCommitSha: String? = null,
        val baselineBuildVersion: String? = null,

        val packageName: String? = null,
        val className: String? = null,
        val methodName: String? = null,
        @Deprecated("Use packageName instead")
        val packageNamePattern: String? = null,
        @Deprecated("Use className instead")
        val classNamePattern: String? = null,

        val testTaskId: String? = null,
        val testTag: String? = null,
        val testPath: String? = null,
        val testName: String? = null,

        val onlyBaselineBuildTestsEnabled: Boolean = false,
        val coverageBranches: List<String> = emptyList(),
        val coverageAppEnvIds: List<String> = emptyList(),
        val coveragePeriodDays: Int? = null,

        val page: Int? = null,
        val pageSize: Int? = null,
    )

    @Resource("/refresh")
    class Refresh(
        val parent: Metrics,
        val reset: Boolean = false
    )

    @Resource("/refresh/{scope}")
    @Deprecated("Use /metrics/refresh instead")
    class RefreshScope(
        val parent: Metrics,
        val scope: String
    )

    @Resource("/refresh-status")
    class RefreshStatus(
        val parent: Metrics,
    )
}

fun Route.metricsRoutes() {
    getApplications()
    getBuilds()
    getBuildDiffReport()
    getRecommendedTests()
    getCoverageTreemap()
    getChangesCoverageTreemap()
    getChanges()
    getCoverage()
    getImpactedTests()
    getImpactedMethods()
}

fun Route.metricsManagementRoutes() {
    postRefreshMetrics()
    postRefreshMetricsWithScope()
    getRefreshStatus()
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

fun Route.getChangesCoverageTreemap() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.ChangesCoverageTreemap> { params ->
        val treemap = metricsService.getChangesCoverageTreemap(
            params.buildId,
            params.baselineBuildId,
            params.testTag,
            params.envId,
            params.branch,
            params.packageNamePattern,
            params.classNamePattern,
            params.rootId,
            params.includeDeleted,
            params.includeEqual
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
            baselineBuildBranches = params.baselineBuildBranches,
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
            includeDeleted = params.includeDeleted,
            includeEqual = params.includeEqual,
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
            groupId = params.groupId,
            appId = params.appId,
            instanceId = params.instanceId,
            commitSha = params.commitSha,
            buildVersion = params.buildVersion,
            testTag = params.testTag,
            envId = params.envId,
            branch = params.branch,
            packageNamePattern = params.packageName,
            classNamePattern = params.className,
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

fun Route.getImpactedTests() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.ImpactedTests> { params ->
        val targetBuild = Build(
            params.groupId,
            params.appId,
            params.instanceId,
            params.commitSha,
            params.buildVersion
        )
        val baselineBuild = BaselineBuild(
            params.groupId,
            params.appId,
            params.baselineInstanceId,
            params.baselineCommitSha,
            params.baselineBuildVersion
        )
        val data = metricsService.getImpactedTests(
            build = targetBuild,
            baselineBuild = baselineBuild,
            testCriteria = TestCriteria(
                testTags = listOfNotNull(params.testTag),
                testTaskId = params.testTaskId,
                testPath = params.testPath,
                testName = params.testName
            ),
            methodCriteria = MethodCriteria(
                packageName = params.packageName ?: params.packageNamePattern,
                className = params.className ?: params.classNamePattern,
                methodName = params.methodName
            ),
            coverageCriteria = CoverageCriteria(
                branches = params.coverageBranches,
                appEnvIds = params.coverageAppEnvIds,
            ),
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

fun Route.getImpactedMethods() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.ImpactedMethods> { params ->
        val targetBuild = Build(
            params.groupId,
            params.appId,
            params.instanceId,
            params.commitSha,
            params.buildVersion
        )
        val baselineBuild = BaselineBuild(
            params.groupId,
            params.appId,
            params.baselineInstanceId,
            params.baselineCommitSha,
            params.baselineBuildVersion
        )
        val data = metricsService.getImpactedMethods(
            build = targetBuild,
            baselineBuild = baselineBuild,
            testCriteria = TestCriteria(
                testTags = listOfNotNull(params.testTag),
                testTaskId = params.testTaskId,
                testPath = params.testPath,
                testName = params.testName
            ),
            methodCriteria = MethodCriteria(
                packageName = params.packageName ?: params.packageNamePattern,
                className = params.className ?: params.classNamePattern,
                methodName = params.methodName
            ),
            coverageCriteria = CoverageCriteria(
                branches = params.coverageBranches,
                appEnvIds = params.coverageAppEnvIds,
            ),
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

fun Route.postRefreshMetrics() {
    val metricsService by closestDI().instance<MetricsService>()

    post<Metrics.Refresh> { params ->
        metricsService.refresh(params.reset)
        call.ok("Metrics were refreshed.")
    }
}

fun Route.postRefreshMetricsWithScope() {
    val metricsService by closestDI().instance<MetricsService>()

    post<Metrics.RefreshScope> { params ->
        metricsService.refresh()
        call.ok("Metrics were refreshed.")
    }
}

fun Route.getRefreshStatus() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.RefreshStatus> { params ->
        val status = metricsService.getRefreshStatus()
        this.call.respond(HttpStatusCode.OK, ApiResponse(status))
    }
}