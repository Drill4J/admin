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
package com.epam.drill.admin.metrics.service

import com.epam.drill.admin.metrics.models.BaselineBuild
import com.epam.drill.admin.metrics.models.Build
import com.epam.drill.admin.metrics.models.BuildSortField
import com.epam.drill.admin.metrics.models.CoverageCriteria
import com.epam.drill.admin.metrics.models.MethodCriteria
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.models.TestCriteria
import com.epam.drill.admin.metrics.views.*
import java.time.Instant

interface MetricsService {
    suspend fun getGroups(): List<String>

    suspend fun getApplications(
        groupId: String? = null,
        freshAfter: Instant? = null,
    ): List<ApplicationView>

    suspend fun getBuilds(
        groupId: String,
        appId: String,
        branches: List<String>,
        envIds: List<String>,
        commitSha: String?,
        buildVersion: String?,
        sortBy: BuildSortField? = null,
        sortOrder: SortOrder? = null,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant? = null,
    ): PagedList<BuildView>

    suspend fun getAppBranches(
        groupId: String,
        appId: String,
        query: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): PagedList<String>

    suspend fun getAppEnvIds(
        groupId: String,
        appId: String,
        query: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): PagedList<String>

    suspend fun getAppTestTags(
        groupId: String,
        appId: String,
        query: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): PagedList<String>

    suspend fun getBuildDetail(buildId: String): BuildDetailView

    suspend fun getBuildCoverageByProbes(
        buildId: String,
        baselineBuildId: String?,
        envIds: List<String>,
        branches: List<String>,
        testTags: List<String>,
        testResults: List<String> = emptyList(),
    ): CoverageUnitSummaryView

    suspend fun getBuildCoverageByMethods(
        buildId: String,
        baselineBuildId: String?,
        envIds: List<String>,
        branches: List<String>,
        testTags: List<String>,
        testResults: List<String> = emptyList(),
    ): CoverageUnitSummaryView

    suspend fun getChangesSummary(
        buildId: String,
        baselineBuildId: String,
    ): ChangesSummaryView

    suspend fun getAppCoverageTrends(
        groupId: String,
        appId: String,
        branches: List<String>,
        envIds: List<String>,
        testTags: List<String>,
        testResults: List<String> = emptyList(),
        size: Int?,
    ): List<CoverageTrendPointView>

    suspend fun getAppChangesTrends(
        groupId: String,
        appId: String,
        baselineBuildId: String,
        branches: List<String>,
        envIds: List<String>,
        testTags: List<String>,
        testResults: List<String> = emptyList(),
        size: Int?,
    ): List<ChangesTrendPointView>

    suspend fun getSimilarBuilds(buildId: String): List<SimilarBuildView>

    suspend fun getBuildTestSessionStats(buildId: String): BuildTestSessionStatsView

    suspend fun getGroupTestSessions(
        groupId: String,
        testTaskIds: List<String>,
        createdBys: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<TestSessionView>

    suspend fun getBuildTestSessions(
        groupId: String,
        buildId: String,
        testTaskIds: List<String>,
        createdBys: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<TestSessionView>

    suspend fun getTestSessionFilterOptions(
        groupId: String,
        buildId: String?,
        field: String,
        query: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): PagedList<String>

    suspend fun getTestSessionDetail(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
    ): TestSessionDetailView

    suspend fun getTestSessionBuilds(
        groupId: String,
        testSessionId: String,
        page: Int? = null,
        pageSize: Int? = null,
    ): PagedList<TestSessionBuildView>

    suspend fun getTestSessionCoverageSummary(
        groupId: String,
        testSessionId: String,
        buildId: String,
        testDefinitionId: String? = null,
    ): TestSessionCoverageSummaryView

    suspend fun getTestSessionDefinitions(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        query: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): PagedList<TestDefinitionView>

    suspend fun getTestLaunches(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        path: String? = null,
        testNames: List<String> = emptyList(),
        testResults: List<String> = emptyList(),
        testTags: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): PagedList<TestLaunchView>

    suspend fun getTestLaunchFilterOptions(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        path: String? = null,
    ): TestLaunchFilterOptionsView

    suspend fun getTestLaunchPage(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        path: String? = null,
        testNames: List<String> = emptyList(),
        testResults: List<String> = emptyList(),
        testTags: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        pageSize: Int? = null,
        launchId: String,
    ): TablePageView

    suspend fun getTestFileLaunches(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        testPaths: List<String> = emptyList(),
        results: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): PagedList<TestFileLaunchView>

    suspend fun getTestFileLaunchFilterOptions(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
    ): TestFileLaunchFilterOptionsView

    suspend fun getTestFileLaunchFilterValues(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        query: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): PagedList<String>

    suspend fun getTestFileLaunchPage(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        testPaths: List<String> = emptyList(),
        results: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        pageSize: Int? = null,
        path: String,
    ): TablePageView

    suspend fun getCoverageTreemap(
        buildId: String,
        testTags: List<String>,
        envIds: List<String>,
        branches: List<String>,
        testResults: List<String> = emptyList(),
        packageNamePattern: String?,
        classNamePattern: String?,
        rootId: String?,
        testSessionId: String? = null,
        testDefinitionId: String? = null,
        includeOtherBuilds: Boolean = true,
        freshAfter: Instant? = null,
    ): List<Any>

    suspend fun getChangesCoverageTreemap(
        buildId: String,
        baselineBuildId: String,
        testTags: List<String>,
        envIds: List<String>,
        branches: List<String>,
        testResults: List<String> = emptyList(),
        packageNamePattern: String?,
        classNamePattern: String?,
        rootId: String?,
        includeDeleted: Boolean?,
        includeEqual: Boolean?,
        includeOtherBuilds: Boolean = true,
        freshAfter: Instant? = null,
    ): List<Any>

    suspend fun getBuildDiffReport(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
        coverageThreshold: Double,
        freshAfter: Instant? = null,
    ): Map<String, Any?>

    // TODO: discuss with team later — replaces main's getChanges() (/changes slim method-diff API).
    suspend fun getBuildChanges(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
        testTags: List<String> = emptyList(),
        testResults: List<String> = emptyList(),
        envIds: List<String> = emptyList(),
        branches: List<String> = emptyList(),
        changeTypes: List<String> = emptyList(),
        hasImpactedTests: Boolean? = null,
        methodSignature: String? = null,
        testDefinitionId: String? = null,
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant? = null,
    ): PagedList<BuildChangeView>

    suspend fun getCoverage(
        buildId: String? = null,
        groupId: String? = null,
        appId: String? = null,
        instanceId: String? = null,
        commitSha: String? = null,
        buildVersion: String? = null,
        testTags: List<String> = emptyList(),
        testResults: List<String> = emptyList(),
        envIds: List<String> = emptyList(),
        branches: List<String> = emptyList(),
        packageNamePattern: String? = null,
        classNamePattern: String? = null,
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        page: Int? = null,
        pageSize: Int? = null,
        testSessionId: String? = null,
        testDefinitionId: String? = null,
        freshAfter: Instant? = null,
    ): PagedList<MethodView>

    suspend fun getCoverageByPackage(
        buildId: String,
        testTags: List<String> = emptyList(),
        testResults: List<String> = emptyList(),
        envIds: List<String> = emptyList(),
        branches: List<String> = emptyList(),
    ): List<PackageCoverageView>

    suspend fun getCoverageByClass(
        buildId: String,
        packageName: String? = null,
        testTags: List<String> = emptyList(),
        testResults: List<String> = emptyList(),
        envIds: List<String> = emptyList(),
        branches: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        page: Int? = null,
        pageSize: Int? = null,
        testSessionId: String? = null,
        testDefinitionId: String? = null,
    ): PagedList<ClassCoverageView>

    suspend fun getImpactedTests(
        build: Build,
        baselineBuild: BaselineBuild,
        testCriteria: TestCriteria = TestCriteria.NONE,
        methodCriteria: MethodCriteria = MethodCriteria.NONE,
        coverageCriteria: CoverageCriteria = CoverageCriteria.NONE,
        impactStatuses: List<TestImpactStatus> = listOf(TestImpactStatus.IMPACTED),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant? = null,
    ): PagedList<TestView>

    suspend fun getImpactedTestsFilterOptions(
        build: Build,
        baselineBuild: BaselineBuild,
        methodCriteria: MethodCriteria = MethodCriteria.NONE,
        coverageCriteria: CoverageCriteria = CoverageCriteria.NONE,
    ): ImpactedTestsFilterOptionsView

    suspend fun getImpactedMethods(
        build: Build,
        baselineBuild: BaselineBuild,
        testCriteria: TestCriteria = TestCriteria.NONE,
        methodCriteria: MethodCriteria = MethodCriteria.NONE,
        coverageCriteria: CoverageCriteria = CoverageCriteria.NONE,
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant? = null,
    ): PagedList<MethodView>
}