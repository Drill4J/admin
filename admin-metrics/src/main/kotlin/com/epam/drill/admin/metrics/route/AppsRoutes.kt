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

import com.epam.drill.admin.metrics.models.CoverageCriteria
import com.epam.drill.admin.metrics.models.MethodCriteria
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.models.TestCriteria
import com.epam.drill.admin.common.config.ApiResponse
import com.epam.drill.admin.common.config.PagedDataResponse
import com.epam.drill.admin.common.config.Paging
import com.epam.drill.admin.metrics.models.BuildSortField
import com.epam.drill.admin.metrics.route.Apps.AppId
import com.epam.drill.admin.metrics.service.MetricsService
import com.epam.drill.admin.metrics.views.MethodView
import com.epam.drill.admin.metrics.views.PagedList
import com.epam.drill.admin.metrics.views.TestImpactStatus
import com.epam.drill.admin.metrics.views.TestView
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Resource("/apps")
class Apps(val parent: Groups.GroupId) {

    @Resource("/{appId}")
    class AppId(val parent: Apps, val appId: String) {
        val groupId = parent.parent.groupId

        @Resource("/branches")
        class AppBranches(
            val parent: AppId,
            val query: String? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
        )

        @Resource("/env-ids")
        class AppEnvIds(
            val parent: AppId,
            val query: String? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
        )

        @Resource("/test-tags")
        class AppTestTags(
            val parent: AppId,
            val query: String? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
        )

        @Resource("/coverage-trends")
        class AppCoverageTrends(
            val parent: AppId,
            val branches: List<String> = emptyList(),
            val envIds: List<String> = emptyList(),
            val testResults: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
            val size: Int? = null,
        )

        @Resource("/changes-trends")
        class AppChangesTrends(
            val parent: AppId,
            val baselineBuildId: String? = null,
            val branches: List<String> = emptyList(),
            val envIds: List<String> = emptyList(),
            val testResults: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
            val size: Int? = null,
        )
    }
}

@Resource("/builds")
class Builds(
    val parent: AppId,

    val groupId: String = parent.parent.parent.groupId,
    val appId: String = parent.appId,

    val branches: List<String> = emptyList(),
    val envIds: List<String> = emptyList(),
    val commitSha: String? = null,
    val buildVersion: String? = null,

    val sortBy: BuildSortField? = null,
    val sortOrder: SortOrder? = null,

    val page: Int? = null,
    val pageSize: Int? = null
) {
    @Resource("/{buildId}")
    class BuildId(
        val parent: Builds,
        val buildId: String
    ) {
        val groupId = parent.groupId
        val appId = parent.appId

        @Resource("/coverage-by-probes")
        class CoverageByProbes(
            val parent: BuildId,
            val baselineBuildId: String? = null,
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testResults: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
        )

        @Resource("/coverage-by-methods")
        class CoverageByMethods(
            val parent: BuildId,
            val baselineBuildId: String? = null,
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testResults: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
        )

        @Resource("/changes/summary")
        class ChangesSummary(
            val parent: BuildId,
            val baselineBuildId: String,
        )

        @Resource("/similar-builds")
        class SimilarBuilds(
            val parent: BuildId,
        )

        @Resource("/test-sessions/summary")
        class TestSessionStats(
            val parent: BuildId,
        )

        @Resource("/test-sessions")
        class TestSessions(
            val parent: BuildId,
            val testTaskIds: List<String> = emptyList(),
            val createdBys: List<String> = emptyList(),
            val results: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
            val sortBy: String? = null,
            val sortOrder: SortOrder? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
        )

        @Resource("/coverage-chart")
        class CoverageTreemap(
            val parent: BuildId,
            val testTags: List<String> = emptyList(),
            val testResults: List<String> = emptyList(),
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
            val packageNamePattern: String? = null,
            val classNamePattern: String? = null,
            val rootId: String? = null,
            val testSessionId: String? = null,
            val testDefinitionId: String? = null,
            val includeOtherBuilds: Boolean = true,
        )

        @Resource("/changes-chart")
        class ChangesCoverageTreemap(
            val parent: BuildId,
            val baselineBuildId: String,
            val testResults: List<String> = emptyList(),
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
            val packageNamePattern: String? = null,
            val classNamePattern: String? = null,
            val rootId: String? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
            val includeDeleted: Boolean? = null,
            val includeEqual: Boolean? = null,
            val includeOtherBuilds: Boolean = true,
        )

        @Resource("/changes")
        class BuildChanges(
            val parent: BuildId,
            val baselineBuildId: String? = null,
            val testResults: List<String> = emptyList(),
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
            val changeTypes: List<String> = emptyList(),
            val hasImpactedTests: Boolean? = null,
            val methodSignature: String? = null,
            val testDefinitionId: String? = null,
            val sortBy: String? = null,
            val sortOrder: SortOrder? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
        )

        @Resource("/methods")
        class Coverage(
            val parent: BuildId,
            val testTags: List<String> = emptyList(),
            val testResults: List<String> = emptyList(),
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
            val packageName: String? = null,
            val className: String? = null,

            val sortBy: String? = null,
            val sortOrder: SortOrder? = null,

            val page: Int? = null,
            val pageSize: Int? = null,

            val testSessionId: String? = null,
            val testDefinitionId: String? = null,
        )

        @Resource("/packages")
        class CoverageByPackage(
            val parent: BuildId,
            val testResults: List<String> = emptyList(),
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
        )

        @Resource("/classes")
        class CoverageByClass(
            val parent: BuildId,
            val packageName: String? = null,
            val testTags: List<String> = emptyList(),
            val testResults: List<String> = emptyList(),
            val envIds: List<String> = emptyList(),
            val branches: List<String> = emptyList(),
            val testProjectIds: List<String> = emptyList(),
            val sortBy: String? = null,
            val sortOrder: SortOrder? = null,
            val page: Int? = null,
            val pageSize: Int? = null,
            val testSessionId: String? = null,
            val testDefinitionId: String? = null,
        )
    }
}

@Serializable
sealed class ImpactedTestFilters(
    val packageName: String? = null,
    val className: String? = null,
    val methodName: String? = null,
    val methodSignature: String? = null,

    val excludeMethodSignatures: List<String> = emptyList(),

    val testTag: String? = null,
    val testPath: String? = null,
    val testName: String? = null,
    val testRunner: String? = null,
    val testTaskId: String? = null,
    val testDefinitionId: String? = null,
    val testProjectId: String? = null,

    val coverageBranches: List<String> = emptyList(),
    val coverageAppEnvIds: List<String> = emptyList(),

    val impactStatuses: List<TestImpactStatus> = listOf(TestImpactStatus.IMPACTED),

    val sortBy: String? = null,
    val sortOrder: SortOrder? = null,

    val page: Int? = null,
    val pageSize: Int? = null,
)

@Resource("/impacted-tests")
@Serializable
class ImpactedTests(
    val parent: Builds.BuildId,
    val baselineBuildId: String? = null,
) : ImpactedTestFilters() {
    @Resource("/filter-options")
    class FilterOptions(val parent: ImpactedTests)
}

@Resource("/impacted-methods")
@Serializable
class ImpactedMethods(
    val parent: Builds.BuildId,
    val baselineBuildId: String? = null,
) : ImpactedTestFilters()

fun Route.appsRoutes() {
    getApps()
    getAppBranches()
    getAppEnvIds()
    getAppTestTags()
    getAppCoverageTrends()
    getAppChangesTrends()
    getBuildTestSessions()
    getBuilds()
    getBuildById()
    getBuildCoverageByProbes()
    getBuildCoverageByMethods()
    getBuildChangesSummary()
    getSimilarBuilds()
    getBuildTestSessionStats()
    getCoverageTreemap()
    getChangesCoverageTreemap()
    getBuildChanges()
    getCoverage()
    getCoverageByPackage()
    getCoverageByClass()
    postImpactedTests()
    getImpactedMethods()
    postImpactedMethods()
}

fun Route.getApps() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Apps> { params ->
        println("!!! getApps")
        val data = metricsService.getApplications(
            groupId = params.parent.groupId,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getAppBranches() {
    val metricsService by closestDI().instance<MetricsService>()

    get<AppId.AppBranches> { params ->
        val data = metricsService.getAppBranches(
            groupId = params.parent.parent.parent.groupId,
            appId = params.parent.appId,
            query = params.query,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respondPagedStrings(data)
    }
}

fun Route.getAppEnvIds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<AppId.AppEnvIds> { params ->
        val data = metricsService.getAppEnvIds(
            groupId = params.parent.parent.parent.groupId,
            appId = params.parent.appId,
            query = params.query,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respondPagedStrings(data)
    }
}

fun Route.getAppTestTags() {
    val metricsService by closestDI().instance<MetricsService>()

    get<AppId.AppTestTags> { params ->
        val data = metricsService.getAppTestTags(
            groupId = params.parent.parent.parent.groupId,
            appId = params.parent.appId,
            query = params.query,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respondPagedStrings(data)
    }
}

fun Route.getAppCoverageTrends() {
    val metricsService by closestDI().instance<MetricsService>()

    get<AppId.AppCoverageTrends> { params ->
        val data = metricsService.getAppCoverageTrends(
            groupId = params.parent.parent.parent.groupId,
            appId = params.parent.appId,
            branches = params.branches,
            envIds = params.envIds,
            testResults = params.testResults,
            testProjectIds = params.testProjectIds,
            size = params.size,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getAppChangesTrends() {
    val metricsService by closestDI().instance<MetricsService>()

    get<AppId.AppChangesTrends> { params ->
        val data = metricsService.getAppChangesTrends(
            groupId = params.parent.parent.parent.groupId,
            appId = params.parent.appId,
            baselineBuildId = params.baselineBuildId.orEmpty(),
            branches = params.branches,
            envIds = params.envIds,
            testResults = params.testResults,
            testProjectIds = params.testProjectIds,
            size = params.size,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}


fun Route.getBuildTestSessions() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.TestSessions> { params ->
        val data = metricsService.getBuildTestSessions(
            groupId = params.parent.parent.groupId,
            buildId = params.parent.buildId,
            testTaskIds = params.testTaskIds,
            createdBys = params.createdBys,
            results = params.results,
            testProjectIds = params.testProjectIds,
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

fun Route.getBuilds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds> { params ->
        val data = metricsService.getBuilds(
            groupId = params.groupId,
            appId = params.appId,
            branches = params.branches,
            envIds = params.envIds,
            commitSha = params.commitSha,
            buildVersion = params.buildVersion,
            sortBy = params.sortBy,
            sortOrder = params.sortOrder,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
            )
        )
    }
}

fun Route.getBuildById() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId> { params ->
        val data = metricsService.getBuildDetail(params.buildId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuildCoverageByProbes() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.CoverageByProbes> { params ->
        val data = metricsService.getBuildCoverageByProbes(
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
            envIds = params.envIds,
            branches = params.branches,
            testResults = params.testResults,
            testProjectIds = params.testProjectIds,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuildCoverageByMethods() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.CoverageByMethods> { params ->
        val data = metricsService.getBuildCoverageByMethods(
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
            envIds = params.envIds,
            branches = params.branches,
            testResults = params.testResults,
            testProjectIds = params.testProjectIds,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuildChangesSummary() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.ChangesSummary> { params ->
        val data = metricsService.getChangesSummary(
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getSimilarBuilds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.SimilarBuilds> { params ->
        val data = metricsService.getSimilarBuilds(params.parent.buildId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuildTestSessionStats() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.TestSessionStats> { params ->
        val data = metricsService.getBuildTestSessionStats(params.parent.buildId)
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getCoverageTreemap() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.CoverageTreemap> { params ->
        val treemap = metricsService.getCoverageTreemap(
            buildId = params.parent.buildId,
            envIds = params.envIds,
            branches = params.branches,
            testProjectIds = params.testProjectIds,
            testResults = params.testResults,
            testTags = params.testTags,
            packageNamePattern = params.packageNamePattern,
            classNamePattern = params.classNamePattern,
            rootId = params.rootId,
            testSessionId = params.testSessionId,
            testDefinitionId = params.testDefinitionId,
            includeOtherBuilds = params.includeOtherBuilds,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(treemap))
    }
}

fun Route.getChangesCoverageTreemap() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.ChangesCoverageTreemap> { params ->
        val treemap = metricsService.getChangesCoverageTreemap(
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
            envIds = params.envIds,
            branches = params.branches,
            testResults = params.testResults,
            testProjectIds = params.testProjectIds,
            packageNamePattern = params.packageNamePattern,
            classNamePattern = params.classNamePattern,
            rootId = params.rootId,
            includeDeleted = params.includeDeleted,
            includeEqual = params.includeEqual,
            includeOtherBuilds = params.includeOtherBuilds,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(treemap))
    }
}


// TODO: discuss with team later — replaces main's getChanges() (/changes slim method-diff API).
fun Route.getBuildChanges() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.BuildChanges> { params ->
        check(params.baselineBuildId != null) { "baselineBuildId is required" }
        val data = metricsService.getBuildChanges(
            groupId = params.parent.groupId,
            appId = params.parent.appId,
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
            testResults = params.testResults,
            envIds = params.envIds,
            branches = params.branches,
            testProjectIds = params.testProjectIds,
            changeTypes = params.changeTypes,
            hasImpactedTests = params.hasImpactedTests,
            methodSignature = params.methodSignature,
            testDefinitionId = params.testDefinitionId,
            sortBy = params.sortBy,
            sortOrder = params.sortOrder,
            page = params.page,
            pageSize = params.pageSize,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
            )
        )
    }
}

fun Route.getCoverage() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.Coverage> { params ->
        val data = metricsService.getCoverage(
            groupId = params.parent.groupId,
            appId = params.parent.appId,
            buildId = params.parent.buildId,
            testTags = params.testTags,
            testResults = params.testResults,
            envIds = params.envIds,
            branches = params.branches,
            testProjectIds = params.testProjectIds,
            packageNamePattern = params.packageName,
            classNamePattern = params.className,
            sortBy = params.sortBy,
            sortOrder = params.sortOrder,
            page = params.page,
            pageSize = params.pageSize,
            testSessionId = params.testSessionId,
            testDefinitionId = params.testDefinitionId,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
            )
        )
    }
}

fun Route.getCoverageByPackage() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.CoverageByPackage> { params ->
        val data = metricsService.getCoverageByPackage(
            buildId = params.parent.buildId,
            testResults = params.testResults,
            envIds = params.envIds,
            branches = params.branches,
            testProjectIds = params.testProjectIds,
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getCoverageByClass() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Builds.BuildId.CoverageByClass> { params ->
        val data = metricsService.getCoverageByClass(
            buildId = params.parent.buildId,
            packageName = params.packageName,
            testTags = params.testTags,
            testResults = params.testResults,
            envIds = params.envIds,
            branches = params.branches,
            testProjectIds = params.testProjectIds,
            sortBy = params.sortBy,
            sortOrder = params.sortOrder,
            page = params.page,
            pageSize = params.pageSize,
            testSessionId = params.testSessionId,
            testDefinitionId = params.testDefinitionId,
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
            )
        )
    }
}

fun Route.postImpactedTests() {
    val metricsService by closestDI().instance<MetricsService>()

    post<ImpactedTests> { params ->
        val filters = call.receive<ImpactedTestFilters>()
        val data = getImpactedTests(
            groupId = params.parent.groupId,
            appId = params.parent.appId,
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
            filters = filters,
            service = metricsService
        )
        call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
            )
        )
    }

    post<ImpactedTests.FilterOptions> { params ->
        val filters = call.receive<ImpactedTests>()
        check(params.parent.baselineBuildId != null) { "baselineBuildId is required" }
        val data = metricsService.getImpactedTestsFilterOptions(
            buildId = params.parent.parent.buildId,
            baselineBuildId = params.parent.baselineBuildId,
            methodCriteria = MethodCriteria(
                packageName = filters.packageName,
                className = filters.className,
                methodName = filters.methodName,
                methodSignature = filters.methodSignature,
                excludeMethodSignatures = filters.excludeMethodSignatures
            ),
            coverageCriteria = CoverageCriteria(
                branches = filters.coverageBranches,
                appEnvIds = filters.coverageAppEnvIds,
            ),
        )
        call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

private suspend fun getImpactedTests(
    groupId: String,
    appId: String,
    buildId: String,
    baselineBuildId: String?,
    filters: ImpactedTestFilters,
    service: MetricsService
): PagedList<TestView> {
    check(baselineBuildId != null) { "baselineBuildId is required" }
    return service.getImpactedTests(
        buildId = buildId,
        baselineBuildId = baselineBuildId,
        testCriteria = TestCriteria(
            testTags = listOfNotNull(filters.testTag),
            testPath = filters.testPath,
            testName = filters.testName,
            testRunner = filters.testRunner,
            testTaskId = filters.testTaskId,
            testDefinitionId = filters.testDefinitionId,
            testProjectId = filters.testProjectId,
        ),
        methodCriteria = MethodCriteria(
            packageName = filters.packageName,
            className = filters.className,
            methodName = filters.methodName,
            methodSignature = filters.methodSignature,
            excludeMethodSignatures = filters.excludeMethodSignatures
        ),
        coverageCriteria = CoverageCriteria(
            branches = filters.coverageBranches,
            appEnvIds = filters.coverageAppEnvIds,
        ),
        impactStatuses = filters.impactStatuses,
        sortBy = filters.sortBy,
        sortOrder = filters.sortOrder,
        page = filters.page,
        pageSize = filters.pageSize,
    )
}

suspend fun ApplicationCall.respondPagedStrings(data: PagedList<String>) {
    respond(
        HttpStatusCode.OK,
        PagedDataResponse(
            data = data.items,
            paging = Paging(
                page = data.page,
                pageSize = data.pageSize,
                total = data.total,
            ),
        ),
    )
}

fun Route.getImpactedMethods() {
    val metricsService by closestDI().instance<MetricsService>()

    get<ImpactedMethods> { params ->
        val data = getImpactedMethods(
            groupId = params.parent.groupId,
            appId = params.parent.appId,
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
            filters = params,
            service = metricsService
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
            )
        )
    }
}

fun Route.postImpactedMethods() {
    val metricsService by closestDI().instance<MetricsService>()

    post<ImpactedMethods> { params ->
        val filters = call.receive<ImpactedTestFilters>()
        val data = getImpactedMethods(
            groupId = params.parent.groupId,
            appId = params.parent.appId,
            buildId = params.parent.buildId,
            baselineBuildId = params.baselineBuildId,
            filters = filters,
            service = metricsService
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
            )
        )
    }
}

private suspend fun getImpactedMethods(
    groupId: String,
    appId: String,
    buildId: String,
    baselineBuildId: String?,
    filters: ImpactedTestFilters,
    service: MetricsService
): PagedList<MethodView> {
    check(baselineBuildId != null) { "baselineBuildId is required" }
    return service.getImpactedMethods(
        buildId = buildId,
        baselineBuildId = baselineBuildId,
        testCriteria = TestCriteria(
            testTags = listOfNotNull(filters.testTag),
            testPath = filters.testPath,
            testName = filters.testName,
            testTaskId = filters.testTaskId,
            testProjectId = filters.testProjectId,
        ),
        methodCriteria = MethodCriteria(
            packageName = filters.packageName,
            className = filters.className,
            methodName = filters.methodName,
        ),
        coverageCriteria = CoverageCriteria(
            branches = filters.coverageBranches,
            appEnvIds = filters.coverageAppEnvIds,
        ),
        sortBy = filters.sortBy,
        sortOrder = filters.sortOrder,
        page = filters.page,
        pageSize = filters.pageSize,
    )
}

