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

import com.epam.drill.admin.common.route.ok
import com.epam.drill.admin.metrics.models.BaselineBuild
import com.epam.drill.admin.metrics.models.Build
import com.epam.drill.admin.metrics.models.CoverageCriteria
import com.epam.drill.admin.metrics.models.MethodCriteria
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.models.TestCriteria
import com.epam.drill.admin.metrics.repository.impl.ApiResponse
import com.epam.drill.admin.metrics.repository.impl.PagedDataResponse
import com.epam.drill.admin.metrics.repository.impl.Paging
import com.epam.drill.admin.metrics.service.MetricsService
import com.epam.drill.admin.metrics.views.MethodView
import com.epam.drill.admin.metrics.views.PagedList
import com.epam.drill.admin.metrics.views.TestView
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.resources.post as postWithParams
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

private val logger = KotlinLogging.logger {}

@Resource("/metrics")
class Metrics {
    @Resource("/groups")
    class Groups(
        val parent: Metrics,
    )

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
        val branches: List<String> = emptyList(),
        val envIds: List<String> = emptyList(),

        val page: Int? = null,
        val pageSize: Int? = null
    )

    @Resource("/builds/{buildId}")
    class BuildById(
        val parent: Metrics = Metrics(),
        val buildId: String,
    ) {
        @Resource("coverage-by-probes")
        class CoverageByProbes(
            val parent: BuildById,
            val baselineBuildId: String? = null,
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testTags: List<String> = emptyList(),
        )

        @Resource("coverage-by-methods")
        class CoverageByMethods(
            val parent: BuildById,
            val baselineBuildId: String? = null,
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testTags: List<String> = emptyList(),
        )

        @Resource("changes-summary")
        class ChangesSummary(
            val parent: BuildById,
            val baselineBuildId: String,
        )

        @Resource("similar-builds")
        class SimilarBuilds(
            val parent: BuildById,
        )

        @Resource("test-session-stats")
        class TestSessionStats(
            val parent: BuildById,
        )
    }

    @Resource("/apps/branches")
    class AppBranches(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
    )

    @Resource("/apps/env-ids")
    class AppEnvIds(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
    )

    @Resource("/apps/test-tags")
    class AppTestTags(
        val parent: Metrics,

        val groupId: String,
        val appId: String,
    )

    @Resource("/coverage-treemap")
    class CoverageTreemap(
        val parent: Metrics,

        val buildId: String,
        val testTags: List<String> = emptyList(),
        val envIds: List<String> = emptyList(),
        val branches: List<String> = emptyList(),
        val packageNamePattern: String? = null,
        val classNamePattern: String? = null,
        val rootId: String? = null,
        val testSessionId: String? = null,
        val testDefinitionId: String? = null
    )

    @Resource("/changes-coverage-treemap")
    class ChangesCoverageTreemap(
        val parent: Metrics,

        val buildId: String,
        val baselineBuildId: String,
        val testTags: List<String> = emptyList(),
        val envIds: List<String> = emptyList(),
        val branches: List<String> = emptyList(),
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
        val coverageThreshold: Double = 0.0,
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

        val buildId: String? = null,
        val groupId: String? = null,
        val appId: String? = null,
        val instanceId: String? = null,
        val commitSha: String? = null,
        val buildVersion: String? = null,
        val testTags: List<String> = emptyList(),
        val envIds: List<String> = emptyList(),
        val branches: List<String> = emptyList(),
        val packageName: String? = null,
        val className: String? = null,

        val page: Int? = null,
        val pageSize: Int? = null
    )

    @Resource("/coverage/by-package")
    class CoverageByPackage(
        val parent: Metrics,

        val buildId: String,
        val testTags: List<String> = emptyList(),
        val envIds: List<String> = emptyList(),
        val branches: List<String> = emptyList(),
    )

    @Resource("/coverage/by-class")
    class CoverageByClass(
        val parent: Metrics,

        val buildId: String,
        val packageName: String? = null,
        val testTags: List<String> = emptyList(),
        val envIds: List<String> = emptyList(),
        val branches: List<String> = emptyList(),
        val sortBy: String? = null,
        val sortOrder: SortOrder? = null,
        val page: Int? = null,
        val pageSize: Int? = null,
    )

    @Resource("/impacted-tests")
    @Serializable
    class ImpactedTests(
        val parent: Metrics = Metrics(),

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

        val excludeMethodSignatures: List<String> = emptyList(),

        val testTaskId: String? = null,
        val testTag: String? = null,
        val testPath: String? = null,
        val testName: String? = null,

        val coverageBranches: List<String> = emptyList(),
        val coverageAppEnvIds: List<String> = emptyList(),

        val sortBy: String? = null,
        val sortOrder: SortOrder? = null,

        val page: Int? = null,
        val pageSize: Int? = null,
    )

    @Resource("/impacted-methods")
    @Serializable
    class ImpactedMethods(
        val parent: Metrics = Metrics(),

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

        val sortBy: String? = null,
        val sortOrder: SortOrder? = null,

        val page: Int? = null,
        val pageSize: Int? = null,
    )
}

fun Route.metricsRoutes() {
    getGroups()
    getApplications()
    getAppBranches()
    getAppEnvIds()
    getAppTestTags()
    getBuilds()
    getBuildById()
    getBuildCoverageByProbes()
    getBuildCoverageByMethods()
    getBuildChangesSummary()
    getSimilarBuilds()
    getBuildTestSessionStats()
    getBuildDiffReport()
    getRecommendedTests()
    getCoverageTreemap()
    getChangesCoverageTreemap()
    getChanges()
    getCoverage()
    getCoverageByPackage()
    getCoverageByClass()
    getImpactedTests()
    postImpactedTests()
    getImpactedMethods()
    postImpactedMethods()
}

fun Route.getGroups() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.Groups> {
        val data = metricsService.getGroups()
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
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

fun Route.getAppBranches() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.AppBranches> { params ->
        val data = metricsService.getAppBranches(params.groupId, params.appId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getAppEnvIds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.AppEnvIds> { params ->
        val data = metricsService.getAppEnvIds(params.groupId, params.appId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getAppTestTags() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.AppTestTags> { params ->
        val data = metricsService.getAppTestTags(params.groupId, params.appId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuilds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.Builds> { params ->
        val data = metricsService.getBuilds(
            params.groupId,
            params.appId,
            params.branches,
            params.envIds,
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

fun Route.getBuildById() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.BuildById> { params ->
        val data = metricsService.getBuildDetail(params.buildId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuildCoverageByProbes() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.BuildById.CoverageByProbes> { params ->
        val data = metricsService.getBuildCoverageByProbes(
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
            envIds = params.envIds,
            branches = params.branches,
            testTags = params.testTags,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuildCoverageByMethods() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.BuildById.CoverageByMethods> { params ->
        val data = metricsService.getBuildCoverageByMethods(
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
            envIds = params.envIds,
            branches = params.branches,
            testTags = params.testTags,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuildChangesSummary() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.BuildById.ChangesSummary> { params ->
        val data = metricsService.getChangesSummary(
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getSimilarBuilds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.BuildById.SimilarBuilds> { params ->
        val data = metricsService.getSimilarBuilds(params.parent.buildId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuildTestSessionStats() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.BuildById.TestSessionStats> { params ->
        val data = metricsService.getBuildTestSessionStats(params.parent.buildId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getCoverageTreemap() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.CoverageTreemap> { params ->
        val treemap = metricsService.getCoverageTreemap(
            params.buildId,
            params.testTags,
            params.envIds,
            params.branches,
            params.packageNamePattern,
            params.classNamePattern,
            params.rootId,
            params.testSessionId,
            params.testDefinitionId
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
            params.testTags,
            params.envIds,
            params.branches,
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
            buildId = params.buildId,
            groupId = params.groupId,
            appId = params.appId,
            instanceId = params.instanceId,
            commitSha = params.commitSha,
            buildVersion = params.buildVersion,
            testTags = params.testTags,
            envIds = params.envIds,
            branches = params.branches,
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

fun Route.getCoverageByPackage() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.CoverageByPackage> { params ->
        val data = metricsService.getCoverageByPackage(
            buildId = params.buildId,
            testTags = params.testTags,
            envIds = params.envIds,
            branches = params.branches,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getCoverageByClass() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.CoverageByClass> { params ->
        val data = metricsService.getCoverageByClass(
            buildId = params.buildId,
            packageName = params.packageName,
            testTags = params.testTags,
            envIds = params.envIds,
            branches = params.branches,
            sortBy = params.sortBy,
            sortOrder = params.sortOrder,
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
        val data = getImpactedTests(params, metricsService)
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data.items,
                Paging(data.page, data.pageSize, data.total)
            )
        )
    }
}

fun Route.postImpactedTests() {
    val metricsService by closestDI().instance<MetricsService>()

    post("metrics/impacted-tests") {
        val data = getImpactedTests(call.receive(), metricsService)
        call.respond(
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
        val data = getImpactedMethods(params, metricsService)
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data.items,
                Paging(data.page, data.pageSize, data.total)
            )
        )
    }
}

fun Route.postImpactedMethods() {
    val metricsService by closestDI().instance<MetricsService>()

    post("metrics/impacted-methods") {
        val data = getImpactedMethods(call.receive(), metricsService)
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data.items,
                Paging(data.page, data.pageSize, data.total)
            )
        )
    }
}

private suspend fun getImpactedTests(
    params: Metrics.ImpactedTests,
    service: MetricsService
): PagedList<TestView> {

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
    return service.getImpactedTests(
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
            methodName = params.methodName,
            excludeMethodSignatures = params.excludeMethodSignatures
        ),
        coverageCriteria = CoverageCriteria(
            branches = params.coverageBranches,
            appEnvIds = params.coverageAppEnvIds,
        ),
        sortBy = params.sortBy,
        sortOrder = params.sortOrder,
        page = params.page,
        pageSize = params.pageSize,
    )
}

private suspend fun getImpactedMethods(
    params: Metrics.ImpactedMethods,
    metricsService: MetricsService
): PagedList<MethodView> {
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
        sortBy = params.sortBy,
        sortOrder = params.sortOrder,
        page = params.page,
        pageSize = params.pageSize,
    )
    return data
}
