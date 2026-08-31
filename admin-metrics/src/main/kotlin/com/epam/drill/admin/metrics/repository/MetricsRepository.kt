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
package com.epam.drill.admin.metrics.repository

import com.epam.drill.admin.metrics.models.BuildSortField
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.views.TestImpactStatus
import java.time.Instant
import java.time.LocalDateTime

interface MetricsRepository {

    suspend fun buildExists(buildId: String): Boolean

    suspend fun getGroups(): List<String>

    suspend fun getApplications(groupId: String? = null): List<Map<String, Any?>>

    suspend fun getBuilds(
        groupId: String,
        appId: String,
        branches: List<String> = emptyList(),
        envIds: List<String> = emptyList(),
        commitSha: String? = null,
        buildVersion: String? = null,
        sortBy: BuildSortField? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null, limit: Int? = null
    ): List<Map<String, Any?>>

    suspend fun getBuildsCount(
        groupId: String, appId: String,
        branches: List<String> = emptyList(),
        envIds: List<String> = emptyList(),
        commitSha: String? = null,
        buildVersion: String? = null,
    ): Long

    suspend fun getAppBranches(
        groupId: String,
        appId: String,
        query: String? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<String>

    suspend fun getAppBranchesCount(groupId: String, appId: String, query: String? = null): Long

    suspend fun getAppEnvIds(
        groupId: String,
        appId: String,
        query: String? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<String>

    suspend fun getAppEnvIdsCount(groupId: String, appId: String, query: String? = null): Long

    suspend fun getAppTestTags(
        groupId: String,
        appId: String,
        query: String? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<String>

    suspend fun getAppTestTagsCount(groupId: String, appId: String, query: String? = null): Long

    suspend fun getBuildDetail(buildId: String): Map<String, Any?>?

    suspend fun getBuildCoverageSummary(
        buildId: String,
        baselineBuildId: String?,
        envIds: List<String>,
        branches: List<String>,
        testTags: List<String>,
    ): Map<String, Any?>?

    suspend fun getAppCoverageTrends(
        groupId: String,
        appId: String,
        branches: List<String> = emptyList(),
        envIds: List<String> = emptyList(),
        testTags: List<String> = emptyList(),
        size: Int = 100,
    ): List<Map<String, Any?>>

    suspend fun getAppChangesTrends(
        groupId: String,
        appId: String,
        baselineBuildId: String,
        branches: List<String> = emptyList(),
        envIds: List<String> = emptyList(),
        testTags: List<String> = emptyList(),
        size: Int = 100,
    ): List<Map<String, Any?>>

    suspend fun getChangesSummary(
        buildId: String,
        baselineBuildId: String,
    ): Map<String, Any?>

    suspend fun getSimilarBuilds(buildId: String): List<Map<String, Any?>>

    suspend fun getBuildTestSessionStats(buildId: String): Map<String, Any?>

    suspend fun getGroupTestSessions(
        groupId: String,
        testTaskIds: List<String> = emptyList(),
        createdBys: List<String> = emptyList(),
        results: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getGroupTestSessionsCount(
        groupId: String,
        testTaskIds: List<String> = emptyList(),
        createdBys: List<String> = emptyList(),
        results: List<String> = emptyList(),
    ): Long

    suspend fun getBuildTestSessions(
        groupId: String,
        buildId: String,
        testTaskIds: List<String> = emptyList(),
        createdBys: List<String> = emptyList(),
        results: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getBuildTestSessionsCount(
        groupId: String,
        buildId: String,
        testTaskIds: List<String> = emptyList(),
        createdBys: List<String> = emptyList(),
        results: List<String> = emptyList(),
    ): Long

    suspend fun getTestSessionTestTaskIds(groupId: String, buildId: String? = null): List<String>

    suspend fun getTestSessionCreatedBys(groupId: String, buildId: String? = null): List<String>

    suspend fun getTestSessionResults(groupId: String, buildId: String? = null): List<String>

    suspend fun getTestSessionFilterValues(
        groupId: String,
        buildId: String? = null,
        field: String,
        query: String? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<String>

    suspend fun getTestSessionFilterValuesCount(
        groupId: String,
        buildId: String? = null,
        field: String,
        query: String? = null,
    ): Long

    suspend fun testSessionExists(groupId: String, testSessionId: String): Boolean

    suspend fun testSessionBuildExists(groupId: String, testSessionId: String, buildId: String): Boolean

    suspend fun getTestSessionDetail(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
    ): Map<String, Any?>?

    suspend fun getTestSessionBuilds(
        groupId: String,
        testSessionId: String,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getTestSessionBuildsCount(
        groupId: String,
        testSessionId: String,
    ): Long

    suspend fun getTestSessionCoverageSummary(
        buildId: String,
        testSessionId: String,
    ): Map<String, Any?>?

    suspend fun getTestDefinitionCoverageSummary(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
    ): Map<String, Any?>?

    suspend fun getTestSessionDefinitions(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        query: String? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getTestSessionDefinitionsCount(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        query: String? = null,
    ): Long

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
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getTestLaunchesCount(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        path: String? = null,
        testNames: List<String> = emptyList(),
        testResults: List<String> = emptyList(),
        testTags: List<String> = emptyList(),
    ): Long

    suspend fun getTestLaunchFilterOptions(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        path: String? = null,
    ): Map<String, List<String>>

    suspend fun getTestLaunchRowNumber(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        path: String? = null,
        testNames: List<String> = emptyList(),
        testResults: List<String> = emptyList(),
        testTags: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        launchId: String,
    ): Long?

    suspend fun getTestFileLaunches(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        testPaths: List<String> = emptyList(),
        results: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getTestFileLaunchesCount(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        testPaths: List<String> = emptyList(),
        results: List<String> = emptyList(),
    ): Long

    suspend fun getTestFileLaunchFilterOptions(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
    ): Map<String, List<String>>

    suspend fun getTestFileLaunchFilterValues(
        groupId: String,
        testSessionId: String,
        query: String? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<String>

    suspend fun getTestFileLaunchFilterValuesCount(
        groupId: String,
        testSessionId: String,
        query: String? = null,
    ): Long

    suspend fun getTestFileLaunchRowNumber(
        groupId: String,
        testSessionId: String,
        buildId: String? = null,
        testPaths: List<String> = emptyList(),
        results: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        path: String,
    ): Long?

    suspend fun getMethodsWithCoverage(
        buildId: String,
        coverageTestTags: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
        coverageBranches: List<String> = emptyList(),
        packageName: String? = null,
        className: String? = null,
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null, limit: Int? = null
    ): List<Map<String, Any?>>

    suspend fun getMethodsWithCoverageByTestSession(
        buildId: String,
        testSessionId: String,
        testTags: List<String> = emptyList(),
        packageNamePattern: String? = null,
        methodSignaturePattern: String? = null,
        coverageAppEnvIds: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getMethodsWithCoverageByTestSessionCount(
        buildId: String,
        testSessionId: String,
        testTags: List<String> = emptyList(),
        packageNamePattern: String? = null,
        methodSignaturePattern: String? = null,
        coverageAppEnvIds: List<String> = emptyList(),
    ): Long

    suspend fun getMethodsWithCoverageByTestDefinition(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
        packageNamePattern: String? = null,
        methodSignaturePattern: String? = null,
        coverageAppEnvIds: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getMethodsWithCoverageByTestDefinitionCount(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
        packageNamePattern: String? = null,
        methodSignaturePattern: String? = null,
        coverageAppEnvIds: List<String> = emptyList(),
    ): Long

    suspend fun getClassCoverageByTestSession(
        buildId: String,
        testSessionId: String,
        packageName: String? = null,
        testTags: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getClassCoverageByTestSessionCount(
        buildId: String,
        testSessionId: String,
        packageName: String? = null,
        testTags: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
    ): Long

    suspend fun getClassCoverageByTestDefinition(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
        packageName: String? = null,
        coverageAppEnvIds: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getClassCoverageByTestDefinitionCount(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
        packageName: String? = null,
        coverageAppEnvIds: List<String> = emptyList(),
    ): Long


    suspend fun getMethodsCount(
        buildId: String,
        packageNamePattern: String? = null,
        classNamePattern: String? = null,
    ): Long

    suspend fun getPackageCoverage(
        buildId: String,
        coverageTestTags: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
        coverageBranches: List<String> = emptyList(),
    ): List<Map<String, Any?>>

    suspend fun getClassCoverage(
        buildId: String,
        packageName: String? = null,
        coverageTestTags: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
        coverageBranches: List<String> = emptyList(),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getClassCoverageCount(
        buildId: String,
        packageName: String? = null,
        coverageTestTags: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
        coverageBranches: List<String> = emptyList(),
    ): Long

    suspend fun getChangesWithCoverage(
        buildId: String,
        baselineBuildId: String? = null,
        coverageTestTags: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
        coverageBranches: List<String> = emptyList(),
        packageName: String? = null,
        className: String? = null,
        offset: Int? = null, limit: Int? = null,
        includeDeleted: Boolean? = null,
        includeEqual: Boolean? = null
    ): List<Map<String, Any?>>

    suspend fun getBuildChanges(
        buildId: String,
        baselineBuildId: String,
        groupId: String,
        appId: String,
        coverageTestTags: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
        coverageBranches: List<String> = emptyList(),
        changeTypes: List<String> = emptyList(),
        hasImpactedTests: Boolean? = null,
        methodSignature: String? = null,
        testDefinitionId: String? = null,
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<Map<String, Any?>>

    suspend fun getBuildChangesCount(
        buildId: String,
        baselineBuildId: String,
        groupId: String,
        appId: String,
        coverageTestTags: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
        coverageBranches: List<String> = emptyList(),
        changeTypes: List<String> = emptyList(),
        hasImpactedTests: Boolean? = null,
        methodSignature: String? = null,
        testDefinitionId: String? = null,
    ): Long

    suspend fun getBuildDiffReport(
        buildId: String,
        baselineBuildId: String,
        coverageThreshold: Double,
    ): Map<String, Any?>

    suspend fun getImpactedTests(
        targetBuildId: String,
        baselineBuildId: String,

        testTags: List<String> = emptyList(),
        testPathPattern: String? = null,
        testNamePattern: String? = null,
        testRunner: String? = null,

        packageNamePattern: String? = null,
        methodSignaturePattern: String? = null,
        excludeMethodSignatures: List<String> = emptyList(),

        coverageBranches: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),

        testDefinitionId: String? = null,
        impactStatuses: List<TestImpactStatus> = listOf(TestImpactStatus.IMPACTED),

        sortBy: String? = null,
        sortOrder: SortOrder? = null,

        offset: Int? = null, limit: Int? = null
    ): List<Map<String, Any?>>

    suspend fun getImpactedTestsCount(
        targetBuildId: String,
        baselineBuildId: String,

        testTaskId: String? = null,
        testTags: List<String> = emptyList(),
        testPathPattern: String? = null,
        testNamePattern: String? = null,
        testRunner: String? = null,

        packageNamePattern: String? = null,
        methodSignaturePattern: String? = null,
        excludeMethodSignatures: List<String> = emptyList(),

        coverageBranches: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),

        testDefinitionId: String? = null,
        impactStatuses: List<TestImpactStatus> = listOf(TestImpactStatus.IMPACTED),
    ): Long

    suspend fun getImpactedMethods(
        targetBuildId: String,
        baselineBuildId: String,

        testTaskId: String? = null,
        testTags: List<String> = emptyList(),
        testPathPattern: String? = null,
        testNamePattern: String? = null,

        packageNamePattern: String? = null,
        methodSignaturePattern: String? = null,
        excludeMethodSignatures: List<String> = emptyList(),

        coverageBranches: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),

        sortBy: String? = null,
        sortOrder: SortOrder? = null,

        offset: Int? = null, limit: Int? = null
    ): List<Map<String, Any?>>

    suspend fun getImpactedTestsFilterOptions(
        targetBuildId: String,
        baselineBuildId: String,
        packageNamePattern: String? = null,
        methodSignaturePattern: String? = null,
        excludeMethodSignatures: List<String> = emptyList(),
        coverageBranches: List<String> = emptyList(),
        coverageAppEnvIds: List<String> = emptyList(),
    ): Map<String, List<String>>

    suspend fun deleteAllBuildDataCreatedBefore(groupId: String, timestamp: Instant)
    suspend fun deleteAllTestDataCreatedBefore(groupId: String, timestamp: Instant)
    suspend fun deleteAllDailyDataCreatedBefore(groupId: String, timestamp: Instant)
    suspend fun deleteAllOrphanReferences(groupId: String, timestamp: Instant)

    suspend fun deleteAllBuildDataByBuildId(groupId: String, appId: String, buildId: String)
    suspend fun deleteAllTestDataByTestSessionId(groupId: String, testSessionId: String)
}