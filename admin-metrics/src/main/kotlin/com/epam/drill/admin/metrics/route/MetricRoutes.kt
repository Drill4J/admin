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

import com.epam.drill.admin.common.config.AnySerializer
import com.epam.drill.admin.metrics.models.BaselineBuild
import com.epam.drill.admin.metrics.models.Build
import com.epam.drill.admin.metrics.models.CoverageCriteria
import com.epam.drill.admin.metrics.models.MethodCriteria
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.models.TestCriteria
import com.epam.drill.admin.common.config.ApiResponse
import com.epam.drill.admin.common.config.Paging
import com.epam.drill.admin.metrics.service.MetricsService
import com.epam.drill.admin.metrics.views.MethodView
import com.epam.drill.admin.metrics.views.PagedList
import com.epam.drill.admin.metrics.views.TestView
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Resource("/metrics")
class Metrics(
    val freshAfter: Long? = null
) {
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
        val rootId: String? = null,
        val testSessionId: String? = null,
        val testDefinitionId: String? = null
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
        val coverageThreshold: Double = 0.0,
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

@Serializable
data class PagedDataWithFreshnessResponse(
    @Serializable(with = AnySerializer::class) val data: Any?,
    val paging: Paging,
    val refreshedAt : Long?
)

fun Route.metricsRoutes() {
    getApplications()
    getBuilds()
    getBuildDiffReport()
    getCoverageTreemap()
    getChangesCoverageTreemap()
    getChanges()
    getCoverage()
    getImpactedTests()
    postImpactedTests()
    getImpactedMethods()
    postImpactedMethods()
}

fun Route.getApplications() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.Applications> { params ->
        val data = metricsService.getApplications(
            groupId = params.groupId,
            freshAfter = params.parent.freshAfter.toInstant(),
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(data))
    }
}

fun Route.getBuilds() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.Builds> { params ->
        val data = metricsService.getBuilds(
            groupId = params.groupId,
            appId = params.appId,
            branch = params.branch,
            envId = params.envId,
            page = params.page,
            pageSize = params.pageSize,
            freshAfter = params.parent.freshAfter.toInstant(),
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataWithFreshnessResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
                refreshedAt = data.refreshedAt?.toEpochMilli()
            )
        )
    }
}

fun Route.getCoverageTreemap() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.CoverageTreemap> { params ->
        val treemap = metricsService.getCoverageTreemap(
            buildId = params.buildId,
            testTag = params.testTag,
            envId = params.envId,
            branch = params.branch,
            packageNamePattern = params.packageNamePattern,
            classNamePattern = params.classNamePattern,
            rootId = params.rootId,
            testSessionId = params.testSessionId,
            testDefinitionId = params.testDefinitionId,
            freshAfter = params.parent.freshAfter.toInstant(),
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(treemap))
    }
}

fun Route.getChangesCoverageTreemap() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.ChangesCoverageTreemap> { params ->
        val treemap = metricsService.getChangesCoverageTreemap(
            buildId = params.buildId,
            baselineBuildId = params.baselineBuildId,
            testTag = params.testTag,
            envId = params.envId,
            branch = params.branch,
            packageNamePattern = params.packageNamePattern,
            classNamePattern = params.classNamePattern,
            rootId = params.rootId,
            includeDeleted = params.includeDeleted,
            includeEqual = params.includeEqual,
            freshAfter = params.parent.freshAfter.toInstant(),
        )
        this.call.respond(HttpStatusCode.OK, ApiResponse(treemap))
    }
}

fun Route.getBuildDiffReport() {
    val metricsService by closestDI().instance<MetricsService>()

    get<Metrics.BuildDiffReport> { params ->
        val report = metricsService.getBuildDiffReport(
            groupId = params.groupId,
            appId = params.appId,
            instanceId = params.instanceId,
            commitSha = params.commitSha,
            buildVersion = params.buildVersion,
            baselineInstanceId = params.baselineInstanceId,
            baselineCommitSha = params.baselineCommitSha,
            baselineBuildVersion = params.baselineBuildVersion,
            coverageThreshold = params.coverageThreshold,
            freshAfter = params.parent.freshAfter.toInstant(),
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
            freshAfter = params.parent.freshAfter.toInstant(),
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataWithFreshnessResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
                refreshedAt = data.refreshedAt?.toEpochMilli()
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
            freshAfter = params.parent.freshAfter.toInstant(),
        )
        this.call.respond(
            HttpStatusCode.OK,
            PagedDataWithFreshnessResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
                refreshedAt = data.refreshedAt?.toEpochMilli()
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
            PagedDataWithFreshnessResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
                refreshedAt = data.refreshedAt?.toEpochMilli()
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
            PagedDataWithFreshnessResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
                refreshedAt = data.refreshedAt?.toEpochMilli()
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
            PagedDataWithFreshnessResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
                refreshedAt = data.refreshedAt?.toEpochMilli()
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
            PagedDataWithFreshnessResponse(
                data = data.items,
                paging = Paging(data.page, data.pageSize, data.total),
                refreshedAt = data.refreshedAt?.toEpochMilli()
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
        freshAfter = params.parent.freshAfter.toInstant(),
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
            testPath = params.testPath,
            testName = params.testName
        ),
        methodCriteria = MethodCriteria(
            packageName = params.packageName,
            className = params.className,
            methodName = params.methodName,
        ),
        coverageCriteria = CoverageCriteria(
            branches = params.coverageBranches,
            appEnvIds = params.coverageAppEnvIds,
        ),
        sortBy = params.sortBy,
        sortOrder = params.sortOrder,
        page = params.page,
        pageSize = params.pageSize,
        freshAfter = params.parent.freshAfter.toInstant(),
    )
    return data
}

private fun Long?.toInstant(): Instant? = this?.let { Instant.ofEpochMilli(it) }
