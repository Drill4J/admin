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
package com.epam.drill.admin.metrics.service.impl

import com.epam.drill.admin.common.exception.BuildNotFound
import com.epam.drill.admin.common.exception.ResourceNotFoundException
import com.epam.drill.admin.common.service.generateBuildId
import com.epam.drill.admin.common.service.parseBuildId
import com.epam.drill.admin.etl.service.EtlService
import com.epam.drill.admin.metrics.config.MetricsConfig
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig.transaction
import com.epam.drill.admin.metrics.config.MetricsServiceUiLinksConfig
import com.epam.drill.admin.metrics.config.TestRecommendationsConfig
import com.epam.drill.admin.metrics.models.BaselineBuild
import com.epam.drill.admin.metrics.models.Build
import com.epam.drill.admin.metrics.models.BuildSortField
import com.epam.drill.admin.metrics.models.CoverageCriteria
import com.epam.drill.admin.metrics.models.MethodCriteria
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.models.TestCriteria
import com.epam.drill.admin.metrics.repository.MetricsRepository
import com.epam.drill.admin.metrics.util.packageNameFromClassName
import com.epam.drill.admin.metrics.util.simpleClassName
import com.epam.drill.admin.metrics.service.MetricsService
import com.epam.drill.admin.metrics.views.*
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.JsonElement
import mu.KotlinLogging
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime

class MetricsServiceImpl(
    private val metricsRepository: MetricsRepository,
    private val metricsServiceUiLinksConfig: MetricsServiceUiLinksConfig,
    private val testRecommendationsConfig: TestRecommendationsConfig,
    private val metricsConfig: MetricsConfig,
    private val etlService: EtlService,
) : MetricsService {

    private val logger = KotlinLogging.logger {}

    override suspend fun getGroups(): List<String> = transaction {
        metricsRepository.getGroups()
    }

    override suspend fun getApplications(groupId: String?, freshAfter: Instant?): List<ApplicationView> {
        //TODO refresh across all groups if groupId is not provided
        if (groupId != null)
            refresh(groupId, freshAfter)
        return transaction {
            metricsRepository.getApplications(groupId).map {
                ApplicationView(
                    groupId = it["group_id"] as String,
                    appId = it["app_id"] as String,
                )
            }
        }
    }

    override suspend fun getBuilds(
        groupId: String,
        appId: String,
        branches: List<String>,
        envIds: List<String>,
        commitSha: String?,
        buildVersion: String?,
        sortBy: BuildSortField?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant?,
    ): PagedList<BuildView> {
        return pagedFreshListOf(groupId, page, pageSize, freshAfter) { offset, limit ->
            metricsRepository.getBuilds(
                groupId, appId,
                branches, envIds,
                commitSha,
                buildVersion,
                sortBy,
                sortOrder,
                offset, limit
            ).map {
                BuildView(
                    id = it["build_id"] as String,
                    groupId = it["group_id"] as String,
                    appId = it["app_id"] as String,
                    buildVersion = it["build_version"] as String?,
                    branch = it["branch"] as String?,
                    envIds = (it["app_env_ids"] as List<String>?) ?: emptyList(),
                    commitSha = it["commit_sha"] as String?,
                    commitDate = (it["committed_at"] as LocalDateTime?)?.toKotlinLocalDateTime(),
                    commitMessage = it["commit_message"] as String?,
                    commitAuthor = it["commit_author"] as String?
                )
            }
        } withTotal {
            metricsRepository.getBuildsCount(
                groupId, appId,
                branches, envIds,
                commitSha,
                buildVersion,
            )
        }
    }

    override suspend fun getAppBranches(
        groupId: String,
        appId: String,
        query: String?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<String> = transaction {
        pagedStringList(page, pageSize, { offset, limit ->
            metricsRepository.getAppBranches(groupId, appId, query, offset, limit)
        }) {
            metricsRepository.getAppBranchesCount(groupId, appId, query)
        }
    }

    override suspend fun getAppEnvIds(
        groupId: String,
        appId: String,
        query: String?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<String> = transaction {
        pagedStringList(page, pageSize, { offset, limit ->
            metricsRepository.getAppEnvIds(groupId, appId, query, offset, limit)
        }) {
            metricsRepository.getAppEnvIdsCount(groupId, appId, query)
        }
    }

    override suspend fun getAppTestTags(
        groupId: String,
        appId: String,
        query: String?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<String> = transaction {
        pagedStringList(page, pageSize, { offset, limit ->
            metricsRepository.getAppTestTags(groupId, appId, query, offset, limit)
        }) {
            metricsRepository.getAppTestTagsCount(groupId, appId, query)
        }
    }

    override suspend fun getBuildDetail(buildId: String): BuildDetailView = transaction {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
        val row = metricsRepository.getBuildDetail(buildId)
            ?: throw BuildNotFound("Build info not found for $buildId")
        mapToBuildDetailView(row)
    }

    override suspend fun getBuildCoverageByProbes(
        buildId: String,
        baselineBuildId: String?,
        envIds: List<String>,
        branches: List<String>,
        testTags: List<String>,
        testResults: List<String>,
    ): CoverageUnitSummaryView = transaction {
        getBuildCoverageUnitSummary(buildId, baselineBuildId, envIds, branches, testTags, testResults, CoverageUnit.PROBES)
    }

    override suspend fun getBuildCoverageByMethods(
        buildId: String,
        baselineBuildId: String?,
        envIds: List<String>,
        branches: List<String>,
        testTags: List<String>,
        testResults: List<String>,
    ): CoverageUnitSummaryView = transaction {
        getBuildCoverageUnitSummary(buildId, baselineBuildId, envIds, branches, testTags, testResults, CoverageUnit.METHODS)
    }

    private suspend fun getBuildCoverageUnitSummary(
        buildId: String,
        baselineBuildId: String?,
        envIds: List<String>,
        branches: List<String>,
        testTags: List<String>,
        testResults: List<String>,
        unit: CoverageUnit,
    ): CoverageUnitSummaryView {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
        baselineBuildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.buildExists(it)) {
                throw BuildNotFound("Baseline build info not found for $it")
            }
        }
        val row = metricsRepository.getBuildCoverageSummary(
            buildId, baselineBuildId, envIds, branches, testTags, testResults
        )
        return CoverageUnitSummaryView(slices = mapToCoverageUnitSlices(row, unit))
    }

    private enum class CoverageUnit { PROBES, METHODS }

    override suspend fun getChangesSummary(
        buildId: String,
        baselineBuildId: String,
    ): ChangesSummaryView = transaction {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
        if (!metricsRepository.buildExists(baselineBuildId)) {
            throw BuildNotFound("Baseline build info not found for $baselineBuildId")
        }
        val row = metricsRepository.getChangesSummary(buildId, baselineBuildId)
        val parsedBuildId = parseBuildId(buildId)
        val impactedTests = metricsRepository.getImpactedTestsCount(
            targetBuildId = buildId,
            baselineBuildId = baselineBuildId,
            impactStatuses = listOf(TestImpactStatus.IMPACTED),
        )
        val impactedMethods = metricsRepository.getBuildChangesCount(
            buildId = buildId,
            baselineBuildId = baselineBuildId,
            groupId = parsedBuildId.groupId,
            appId = parsedBuildId.appId,
            hasImpactedTests = true,
        )
        ChangesSummaryView(
            modifiedMethods = (row["modified_methods"] as? Number)?.toInt() ?: 0,
            newMethods = (row["new_methods"] as? Number)?.toInt() ?: 0,
            deletedMethods = (row["deleted_methods"] as? Number)?.toInt() ?: 0,
            impactedTests = impactedTests.toInt(),
            impactedMethods = impactedMethods.toInt(),
        )
    }

    override suspend fun getAppCoverageTrends(
        groupId: String,
        appId: String,
        branches: List<String>,
        envIds: List<String>,
        testTags: List<String>,
        testResults: List<String>,
        size: Int?,
    ): List<CoverageTrendPointView> = transaction {
        metricsRepository.getAppCoverageTrends(
            groupId = groupId,
            appId = appId,
            branches = branches,
            envIds = envIds,
            testTags = testTags,
            testResults = testResults,
            size = normalizeTrendSize(size),
        ).map { row ->
            val isolated = ratioToPercent(row["isolated_probes_coverage_ratio"])
            val aggregated = ratioToPercent(row["aggregated_probes_coverage_ratio"])
                .coerceAtLeast(isolated)
            CoverageTrendPointView(
                buildId = row["build_id"] as String,
                buildLabel = row["build_label"] as? String ?: row["build_id"] as String,
                buildDate = (row["build_date"] as LocalDateTime?)?.toKotlinLocalDateTime(),
                isolatedCoveragePercent = isolated,
                otherBuildsCoveragePercent = (aggregated - isolated).coerceAtLeast(0.0),
                aggregatedCoveragePercent = aggregated,
            )
        }
    }

    override suspend fun getAppChangesTrends(
        groupId: String,
        appId: String,
        baselineBuildId: String,
        branches: List<String>,
        envIds: List<String>,
        testTags: List<String>,
        testResults: List<String>,
        size: Int?,
    ): List<ChangesTrendPointView> = transaction {
        require(baselineBuildId.isNotBlank()) {
            "baselineBuildId is required for changes trends"
        }
        if (!metricsRepository.buildExists(baselineBuildId)) {
            throw BuildNotFound("Baseline build info not found for $baselineBuildId")
        }
        metricsRepository.getAppChangesTrends(
            groupId = groupId,
            appId = appId,
            baselineBuildId = baselineBuildId,
            branches = branches,
            envIds = envIds,
            testTags = testTags,
            testResults = testResults,
            size = normalizeTrendSize(size),
        ).map { row ->
            val coveredProbes = (row["isolated_covered_probes"] as? Number)?.toInt() ?: 0
            val aggregatedProbes = (row["aggregated_covered_probes"] as? Number)?.toInt() ?: 0
            val coveredMethods = (row["isolated_tested_methods"] as? Number)?.toInt() ?: 0
            val aggregatedMethods = (row["aggregated_tested_methods"] as? Number)?.toInt() ?: 0
            ChangesTrendPointView(
                buildId = row["build_id"] as String,
                buildLabel = row["build_label"] as? String ?: row["build_id"] as String,
                buildDate = (row["build_date"] as LocalDateTime?)?.toKotlinLocalDateTime(),
                totalProbes = (row["total_probes"] as? Number)?.toInt() ?: 0,
                coveredProbes = coveredProbes,
                coveredInOtherBuildsProbes = aggregatedProbes.coerceAtLeast(coveredProbes),
                totalMethods = (row["total_methods"] as? Number)?.toInt() ?: 0,
                coveredMethods = coveredMethods,
                coveredInOtherBuildsMethods = aggregatedMethods.coerceAtLeast(coveredMethods),
            )
        }
    }

    override suspend fun getSimilarBuilds(buildId: String): List<SimilarBuildView> = transaction {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
        metricsRepository.getSimilarBuilds(buildId).map { row ->
            val equalMethods = (row["target_equal_methods"] as? Number)?.toLong() ?: 0
            val totalMethods = (row["target_total_methods"] as? Number)?.toLong() ?: 0
            val identityRatio = (row["identity_ratio"] as? Number)?.toDouble() ?: 0.0
            SimilarBuildView(
                buildId = row["build_id"] as String,
                versionId = row["version_id"] as? String,
                buildVersion = row["build_version"] as? String,
                branch = row["branch"] as? String,
                identityRatio = identityRatio,
                changesDescription = "$equalMethods / $totalMethods methods (${(identityRatio * 100).toInt()}%)",
            )
        }
    }

    override suspend fun getBuildTestSessionStats(buildId: String): BuildTestSessionStatsView = transaction {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
        val row = metricsRepository.getBuildTestSessionStats(buildId)
        BuildTestSessionStatsView(
            sessionCount = (row["session_count"] as? Number)?.toInt() ?: 0,
            testRunCount = (row["test_run_count"] as? Number)?.toInt() ?: 0,
        )
    }

    override suspend fun getGroupTestSessions(
        groupId: String,
        testTaskIds: List<String>,
        createdBys: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<TestSessionView> = transaction {
        val validatedSortBy = validateTestSessionSortBy(sortBy)
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getGroupTestSessions(
                groupId = groupId,
                testTaskIds = testTaskIds,
                createdBys = createdBys,
                results = results,
                sortBy = validatedSortBy,
                sortOrder = sortOrder,
                offset = offset,
                limit = limit,
            ).map(::mapToTestSessionView)
        } withTotal {
            metricsRepository.getGroupTestSessionsCount(
                groupId = groupId,
                testTaskIds = testTaskIds,
                createdBys = createdBys,
                results = results,
            )
        }
    }

    override suspend fun getBuildTestSessions(
        groupId: String,
        buildId: String,
        testTaskIds: List<String>,
        createdBys: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<TestSessionView> = transaction {
        val validatedSortBy = validateTestSessionSortBy(sortBy)
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getBuildTestSessions(
                groupId = groupId,
                buildId = buildId,
                testTaskIds = testTaskIds,
                createdBys = createdBys,
                results = results,
                sortBy = validatedSortBy,
                sortOrder = sortOrder,
                offset = offset,
                limit = limit,
            ).map(::mapToTestSessionView)
        } withTotal {
            metricsRepository.getBuildTestSessionsCount(
                groupId = groupId,
                buildId = buildId,
                testTaskIds = testTaskIds,
                createdBys = createdBys,
                results = results,
            )
        }
    }

    override suspend fun getTestSessionFilterOptions(
        groupId: String,
        buildId: String?,
        field: String,
        query: String?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<String> = transaction {
        val validatedField = validateTestSessionFilterField(field)
        pagedStringList(page, pageSize, { offset, limit ->
            metricsRepository.getTestSessionFilterValues(groupId, buildId, validatedField, query, offset, limit)
        }) {
            metricsRepository.getTestSessionFilterValuesCount(groupId, buildId, validatedField, query)
        }
    }

    override suspend fun getTestSessionDetail(
        groupId: String,
        testSessionId: String,
        buildId: String?,
    ): TestSessionDetailView = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        buildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, it)) {
                throw ResourceNotFoundException("Test session $testSessionId is not linked to build $it")
            }
        }
        val row = metricsRepository.getTestSessionDetail(groupId, testSessionId, buildId)
            ?: throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        mapToTestSessionDetailView(row)
    }

    override suspend fun getTestSessionBuilds(
        groupId: String,
        testSessionId: String,
        page: Int?,
        pageSize: Int?,
    ): PagedList<TestSessionBuildView> = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getTestSessionBuilds(
                groupId = groupId,
                testSessionId = testSessionId,
                offset = offset,
                limit = limit,
            ).map { row ->
                TestSessionBuildView(
                    appId = row["app_id"] as String,
                    buildId = row["build_id"] as String,
                    buildVersion = row["build_version"] as? String,
                    branch = row["branch"] as? String,
                    coveredProbes = (row["covered_probes"] as? Number)?.toInt() ?: 0,
                    totalProbes = (row["total_probes"] as? Number)?.toInt() ?: 0,
                    coveredMethods = (row["covered_methods"] as? Number)?.toInt() ?: 0,
                    totalMethods = (row["total_methods"] as? Number)?.toInt() ?: 0,
                )
            }
        } withTotal {
            metricsRepository.getTestSessionBuildsCount(groupId, testSessionId)
        }
    }

    override suspend fun getTestSessionCoverageSummary(
        groupId: String,
        testSessionId: String,
        buildId: String,
        testDefinitionId: String?,
    ): TestSessionCoverageSummaryView {
        if (!testDefinitionId.isNullOrBlank()) {
            testDefinitionCoverageEtl.run(
                EtlContext(
                    groupId = groupId,
                    testSessionId = testSessionId,
                    testDefinitionId = testDefinitionId,
                )
            )
        }
        return transaction {
            validateTestSessionBuild(groupId, testSessionId, buildId)
            val row = if (testDefinitionId.isNullOrBlank()) {
                metricsRepository.getTestSessionCoverageSummary(buildId, testSessionId)
            } else {
                metricsRepository.getTestDefinitionCoverageSummary(buildId, testSessionId, testDefinitionId)
            }
            mapToTestSessionCoverageSummaryView(row)
        }
    }

    override suspend fun getTestSessionDefinitions(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        query: String?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<TestDefinitionView> = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        buildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, it)) {
                throw ResourceNotFoundException("Test session $testSessionId is not linked to build $it")
            }
        }
        val searchQuery = query?.takeIf { it.isNotBlank() }
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getTestSessionDefinitions(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
                query = searchQuery,
                offset = offset,
                limit = limit,
            ).map { row ->
                TestDefinitionView(
                    testDefinitionId = row["test_definition_id"] as String,
                    testName = row["test_name"] as? String,
                    testPath = row["test_path"] as? String,
                    testRunner = row["test_runner"] as? String,
                    testResult = row["test_result"] as String,
                    testLaunches = (row["test_launches"] as? Number)?.toInt() ?: 0,
                )
            }
        } withTotal {
            metricsRepository.getTestSessionDefinitionsCount(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
                query = searchQuery,
            )
        }
    }

    override suspend fun getTestLaunches(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        path: String?,
        testNames: List<String>,
        testResults: List<String>,
        testTags: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<TestLaunchView> = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        buildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, it)) {
                throw ResourceNotFoundException("Test session $testSessionId is not linked to build $it")
            }
        }
        val validatedSortBy = validateTestLaunchSortBy(sortBy)
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getTestLaunches(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
                path = path,
                testNames = testNames,
                testResults = testResults,
                testTags = testTags,
                sortBy = validatedSortBy,
                sortOrder = sortOrder,
                offset = offset,
                limit = limit,
            ).map { row -> mapToTestLaunchView(row) }
        } withTotal {
            metricsRepository.getTestLaunchesCount(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
                path = path,
                testNames = testNames,
                testResults = testResults,
                testTags = testTags,
            )
        }
    }

    override suspend fun getTestLaunchFilterOptions(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        path: String?,
    ): TestLaunchFilterOptionsView = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        buildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, it)) {
                throw ResourceNotFoundException("Test session $testSessionId is not linked to build $it")
            }
        }
        val options = metricsRepository.getTestLaunchFilterOptions(
            groupId = groupId,
            testSessionId = testSessionId,
            buildId = buildId,
            path = path,
        )
        TestLaunchFilterOptionsView(
            testNames = options.getValue("testNames"),
            testTags = options.getValue("testTags"),
            testResults = options.getValue("testResults"),
        )
    }

    override suspend fun getTestLaunchPage(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        path: String?,
        testNames: List<String>,
        testResults: List<String>,
        testTags: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        pageSize: Int?,
        launchId: String,
    ): TablePageView = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        buildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, it)) {
                throw ResourceNotFoundException("Test session $testSessionId is not linked to build $it")
            }
        }
        if (launchId.isBlank()) {
            throw IllegalArgumentException("launchId is required")
        }
        val validatedSortBy = validateTestLaunchSortBy(sortBy)
        val rowNumber = metricsRepository.getTestLaunchRowNumber(
            groupId = groupId,
            testSessionId = testSessionId,
            buildId = buildId,
            path = path,
            testNames = testNames,
            testResults = testResults,
            testTags = testTags,
            sortBy = validatedSortBy,
            sortOrder = sortOrder,
            launchId = launchId,
        ) ?: throw ResourceNotFoundException("Test launch $launchId was not found")
        TablePageView(page = pageFromRowNumber(rowNumber, pageSize))
    }

    override suspend fun getTestFileLaunches(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        testPaths: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<TestFileLaunchView> = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        buildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, it)) {
                throw ResourceNotFoundException("Test session $testSessionId is not linked to build $it")
            }
        }
        val validatedSortBy = validateTestFileLaunchSortBy(sortBy)
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getTestFileLaunches(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
                testPaths = testPaths,
                results = results,
                sortBy = validatedSortBy,
                sortOrder = sortOrder,
                offset = offset,
                limit = limit,
            ).map { row -> mapToTestFileLaunchView(row) }
        } withTotal {
            metricsRepository.getTestFileLaunchesCount(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
                testPaths = testPaths,
                results = results,
            )
        }
    }

    override suspend fun getTestFileLaunchFilterOptions(
        groupId: String,
        testSessionId: String,
        buildId: String?,
    ): TestFileLaunchFilterOptionsView = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        buildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, it)) {
                throw ResourceNotFoundException("Test session $testSessionId is not linked to build $it")
            }
        }
        val options = metricsRepository.getTestFileLaunchFilterOptions(
            groupId = groupId,
            testSessionId = testSessionId,
            buildId = buildId,
        )
        TestFileLaunchFilterOptionsView(
            testPaths = options.getValue("testPaths"),
            results = options.getValue("results"),
        )
    }

    override suspend fun getTestFileLaunchFilterValues(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        query: String?,
        page: Int?,
        pageSize: Int?,
    ): PagedList<String> = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        buildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, it)) {
                throw ResourceNotFoundException("Test session $testSessionId is not linked to build $it")
            }
        }
        pagedStringList(page, pageSize, { offset, limit ->
            metricsRepository.getTestFileLaunchFilterValues(groupId, testSessionId, query, offset, limit)
        }) {
            metricsRepository.getTestFileLaunchFilterValuesCount(groupId, testSessionId, query)
        }
    }

    override suspend fun getTestFileLaunchPage(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        testPaths: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        pageSize: Int?,
        path: String,
    ): TablePageView = transaction {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        buildId?.takeIf { it.isNotBlank() }?.let {
            if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, it)) {
                throw ResourceNotFoundException("Test session $testSessionId is not linked to build $it")
            }
        }
        if (path.isBlank()) {
            throw IllegalArgumentException("path is required")
        }
        val validatedSortBy = validateTestFileLaunchSortBy(sortBy)
        val rowNumber = metricsRepository.getTestFileLaunchRowNumber(
            groupId = groupId,
            testSessionId = testSessionId,
            buildId = buildId,
            testPaths = testPaths,
            results = results,
            sortBy = validatedSortBy,
            sortOrder = sortOrder,
            path = path,
        ) ?: throw ResourceNotFoundException("Test file $path was not found")
        TablePageView(page = pageFromRowNumber(rowNumber, pageSize))
    }

    override suspend fun getCoverageTreemap(
        buildId: String,
        testTags: List<String>,
        envIds: List<String>,
        branches: List<String>,
        testResults: List<String>,
        packageNamePattern: String?,
        classNamePattern: String?,
        rootId: String?,
        testSessionId: String?,
        testDefinitionId: String?,
        includeOtherBuilds: Boolean,
        freshAfter: Instant?,
    ): List<Any> {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
        val methodCriteria = MethodCriteria(
            packageName = packageNamePattern,
            className = classNamePattern
        )

        refresh(parseBuildId(buildId).groupId, freshAfter)

        val data = when {
            testDefinitionId != null -> {
                val resolvedTestSessionId = testSessionId
                    ?: throw IllegalArgumentException("testSessionId is required when testDefinitionId is specified")
                etlService.loadTestDefinitionCoverage(
                    groupId = parseBuildId(buildId).groupId,
                    testSessionId = testSessionId,
                    testDefinitionId = testDefinitionId,
                    snapshotTimestamp = freshAfter ?: Instant.now(),
                )
                metricsRepository.getMethodsWithCoverageByTestDefinition(
                    buildId = buildId,
                    testSessionId = resolvedTestSessionId,
                    testDefinitionId = testDefinitionId,
                    packageNamePattern = methodCriteria.packageNamePattern,
                    methodSignaturePattern = methodCriteria.signaturePattern,
                    coverageAppEnvIds = envIds,
                )
            }

            testSessionId != null -> {
                metricsRepository.getMethodsWithCoverageByTestSession(
                    buildId = buildId,
                    testSessionId = testSessionId,
                    packageNamePattern = methodCriteria.packageNamePattern,
                    methodSignaturePattern = methodCriteria.signaturePattern,
                    coverageAppEnvIds = envIds,
                    testTags = testTags,
                )
            }

            else -> {
                metricsRepository.getMethodsWithCoverage(
                    buildId = buildId,
                    coverageTestTags = testTags,
                    coverageAppEnvIds = envIds,
                    coverageBranches = branches,
                    coverageTestResults = testResults,
                    packageName = packageNamePattern?.takeIf { it.isNotBlank() },
                    className = classNamePattern?.takeIf { it.isNotBlank() }
                )
            }
        }

        return buildTree(
            data,
            rootId,
            if (includeOtherBuilds) TreemapCoveredProbesKey.AGGREGATED else TreemapCoveredProbesKey.ISOLATED,
        )
    }

    override suspend fun getChangesCoverageTreemap(
        buildId: String,
        baselineBuildId: String,
        testTags: List<String>,
        envIds: List<String>,
        branches: List<String>,
        testResults: List<String>,
        packageNamePattern: String?,
        classNamePattern: String?,
        rootId: String?,
        includeDeleted: Boolean?,
        includeEqual: Boolean?,
        includeOtherBuilds: Boolean,
        freshAfter: Instant?,
    ): List<Any> {

        if (!metricsRepository.buildExists(baselineBuildId)) {
            throw BuildNotFound("Baseline build info not found for $baselineBuildId")
        }

        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }

        refresh(parseBuildId(buildId).groupId, freshAfter)

        val data = metricsRepository.getChangesWithCoverage(
            buildId = buildId,
            baselineBuildId = baselineBuildId,
            coverageTestTags = testTags,
            coverageAppEnvIds = envIds,
            coverageBranches = branches,
            coverageTestResults = testResults,
            packageName = packageNamePattern?.takeIf { it.isNotBlank() },
            className = classNamePattern?.takeIf { it.isNotBlank() },
            includeDeleted = includeDeleted?.takeIf { it },
            includeEqual = includeEqual?.takeIf { it },
        )

        return buildTree(
            data,
            rootId,
            if (includeOtherBuilds) TreemapCoveredProbesKey.AGGREGATED else TreemapCoveredProbesKey.ISOLATED,
        )
    }

    override suspend fun getBuildDiffReport(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
        coverageThreshold: Double,
        freshAfter: Instant?,
    ): Map<String, Any?> {
        return transaction {

            val baselineBuildId = generateBuildId(
                groupId,
                appId,
                baselineInstanceId,
                baselineCommitSha,
                baselineBuildVersion,
                """
                Provide at least one the following: baselineInstanceId, baselineCommitSha, baselineBuildVersion
                """.trimIndent()
            )

            if (!metricsRepository.buildExists(baselineBuildId)) {
                throw BuildNotFound("Baseline build info not found for $baselineBuildId")
            }

            val buildId = generateBuildId(groupId, appId, instanceId, commitSha, buildVersion)
            if (!metricsRepository.buildExists(buildId)) {
                throw BuildNotFound("Build info not found for $buildId")
            }

            refresh(groupId, freshAfter)

            val metrics = metricsRepository.getBuildDiffReport(
                buildId,
                baselineBuildId,
                coverageThreshold
            )

            val baseUrl = metricsServiceUiLinksConfig.baseUrl
            val buildTestingReportPath = metricsServiceUiLinksConfig.buildTestingReportPath
            val buildRisksReportPath = metricsServiceUiLinksConfig.buildChangesReportPath
            val impactedTestsReportPath = metricsServiceUiLinksConfig.impactedTestsReportPath
            mapOf(
                "inputParameters" to mapOf(
                    "groupId" to groupId,
                    "appId" to appId,
                    "instanceId" to instanceId,
                    "commitSha" to commitSha,
                    "buildVersion" to buildVersion,
                    "baselineInstanceId" to baselineInstanceId,
                    "baselineCommitSha" to baselineCommitSha,
                    "baselineBuildVersion" to baselineBuildVersion
                ),
                "inferredValues" to mapOf(
                    "build" to buildId,
                    "baselineBuild" to baselineBuildId,
                ),
                "metrics" to metrics,
                "links" to baseUrl?.run {
                    mapOf(
                        "changes" to buildRisksReportPath?.run {
                            getUriString(
                                baseUrl = baseUrl,
                                path = buildRisksReportPath,
                                queryParams = mapOf(
                                    "build" to buildId,
                                    "baseline_build" to baselineBuildId,
                                )
                            )
                        },
                        "impacted_tests" to impactedTestsReportPath?.run {
                            getUriString(
                                baseUrl = baseUrl,
                                path = this,
                                queryParams = mapOf(
                                    "build" to buildId,
                                    "baseline_build" to baselineBuildId,
                                )
                            )
                        },
                        "build" to buildTestingReportPath?.run {
                            getUriString(
                                baseUrl = baseUrl,
                                path = this,
                                queryParams = mapOf(
                                    "build" to buildId,
                                )
                            )
                        },
                        "baseline_build" to buildTestingReportPath?.run {
                            getUriString(
                                baseUrl = baseUrl,
                                path = this,
                                queryParams = mapOf(
                                    "build" to baselineBuildId,
                                )
                            )
                        },
                        "full_report" to buildTestingReportPath?.run {
                            getUriString(
                                baseUrl = baseUrl,
                                path = this,
                                queryParams = mapOf(
                                    "build" to buildId,
                                    "baseline_build" to baselineBuildId
                                )
                            )
                        }
                    )
                }
            )
        }
    }

    // TODO: discuss with team later — replaces main's getChanges() (/changes slim method-diff API).
    override suspend fun getBuildChanges(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
        testTags: List<String>,
        testResults: List<String>,
        envIds: List<String>,
        branches: List<String>,
        changeTypes: List<String>,
        hasImpactedTests: Boolean?,
        methodSignature: String?,
        testDefinitionId: String?,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant?,
    ): PagedList<BuildChangeView> {
        val validatedSortBy = validateBuildChangeSortBy(sortBy)
        val baselineBuildId = generateBuildId(
            groupId,
            appId,
            baselineInstanceId,
            baselineCommitSha,
            baselineBuildVersion,
            """
                Provide at least one the following: baselineInstanceId, baselineCommitSha, baselineBuildVersion
                """.trimIndent()
        )

        if (!metricsRepository.buildExists(baselineBuildId)) {
            throw BuildNotFound("Baseline build info not found for $baselineBuildId")
        }

        val buildId = generateBuildId(groupId, appId, instanceId, commitSha, buildVersion)
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }

        val normalizedChangeTypes = changeTypes.map { it.trim().lowercase() }.filter { it.isNotBlank() }

        return pagedFreshListOf(groupId, page, pageSize, freshAfter) { offset, limit ->
            metricsRepository.getBuildChanges(
                buildId = buildId,
                baselineBuildId = baselineBuildId,
                groupId = groupId,
                appId = appId,
                coverageTestTags = testTags,
                coverageAppEnvIds = envIds,
                coverageBranches = branches,
                coverageTestResults = testResults,
                changeTypes = normalizedChangeTypes,
                hasImpactedTests = hasImpactedTests,
                methodSignature = methodSignature?.takeIf { it.isNotBlank() },
                testDefinitionId = testDefinitionId?.takeIf { it.isNotBlank() },
                sortBy = validatedSortBy,
                sortOrder = sortOrder,
                offset = offset,
                limit = limit,
            ).map(::mapToBuildChangeView)
        } withTotal {
            metricsRepository.getBuildChangesCount(
                buildId = buildId,
                baselineBuildId = baselineBuildId,
                groupId = groupId,
                appId = appId,
                coverageTestTags = testTags,
                coverageAppEnvIds = envIds,
                coverageBranches = branches,
                coverageTestResults = testResults,
                changeTypes = normalizedChangeTypes,
                hasImpactedTests = hasImpactedTests,
                methodSignature = methodSignature?.takeIf { it.isNotBlank() },
                testDefinitionId = testDefinitionId?.takeIf { it.isNotBlank() },
            )
        }
    }

    override suspend fun getCoverage(
        buildId: String?,
        groupId: String?,
        appId: String?,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        testTags: List<String>,
        testResults: List<String>,
        envIds: List<String>,
        branches: List<String>,
        packageNamePattern: String?,
        classNamePattern: String?,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
        testSessionId: String?,
        testDefinitionId: String?,
        freshAfter: Instant?,
    ): PagedList<MethodView> {
        val resolvedBuildId = buildId?.takeIf { it.isNotBlank() }
            ?: generateBuildId(groupId!!, appId!!, instanceId, commitSha, buildVersion)
        if (!metricsRepository.buildExists(resolvedBuildId)) {
            throw BuildNotFound("Build info not found for $resolvedBuildId")
        }

        val resolvedGroupId = groupId?.takeIf { it.isNotBlank() } ?: parseBuildId(resolvedBuildId).groupId
        val freshness = refresh(resolvedGroupId, freshAfter)

        val packageFilter = packageNamePattern?.takeIf { it.isNotBlank() }
        val classFilter = classNamePattern?.takeIf { it.isNotBlank() }
        val methodCriteria = MethodCriteria(packageName = packageFilter, className = classFilter)

        val sessionSortMapping = mapOf(
            "coverageRatio" to "probes_coverage_ratio",
            "probesCount" to "probes_count",
            "coveredProbes" to "covered_probes",
        )
        val mappedSortBy = sortBy?.let { sessionSortMapping[it] }

        val result = transaction {
            when {
                testDefinitionId != null -> {
                    val resolvedTestSessionId = testSessionId
                        ?: throw IllegalArgumentException("testSessionId is required when testDefinitionId is specified")
                    validateTestSessionBuildForCoverage(resolvedTestSessionId, resolvedBuildId)

                    return@transaction pagedListOf(
                        page = page ?: 1,
                        pageSize = pageSize ?: metricsConfig.pageSize
                    ) { offset, limit ->
                        metricsRepository.getMethodsWithCoverageByTestDefinition(
                            buildId = resolvedBuildId,
                            testSessionId = resolvedTestSessionId,
                            testDefinitionId = testDefinitionId,
                            packageNamePattern = methodCriteria.packageNamePattern,
                            methodSignaturePattern = methodCriteria.signaturePattern,
                            coverageAppEnvIds = envIds,
                            sortBy = mappedSortBy,
                            sortOrder = sortOrder,
                            offset = offset,
                            limit = limit,
                        ).map(::mapToMethodView)
                    } withTotal {
                        metricsRepository.getMethodsWithCoverageByTestDefinitionCount(
                            buildId = resolvedBuildId,
                            testSessionId = resolvedTestSessionId,
                            testDefinitionId = testDefinitionId,
                            packageNamePattern = methodCriteria.packageNamePattern,
                            methodSignaturePattern = methodCriteria.signaturePattern,
                            coverageAppEnvIds = envIds,
                        )
                    }
                }
                testSessionId != null -> {
                    validateTestSessionBuildForCoverage(testSessionId, resolvedBuildId)

                    return@transaction pagedListOf(
                        page = page ?: 1,
                        pageSize = pageSize ?: metricsConfig.pageSize
                    ) { offset, limit ->
                        metricsRepository.getMethodsWithCoverageByTestSession(
                            buildId = resolvedBuildId,
                            testSessionId = testSessionId,
                            testTags = testTags,
                            packageNamePattern = methodCriteria.packageNamePattern,
                            methodSignaturePattern = methodCriteria.signaturePattern,
                            coverageAppEnvIds = envIds,
                            sortBy = mappedSortBy,
                            sortOrder = sortOrder,
                            offset = offset,
                            limit = limit,
                        ).map(::mapToMethodView)
                    } withTotal {
                        metricsRepository.getMethodsWithCoverageByTestSessionCount(
                            buildId = resolvedBuildId,
                            testSessionId = testSessionId,
                            testTags = testTags,
                            packageNamePattern = methodCriteria.packageNamePattern,
                            methodSignaturePattern = methodCriteria.signaturePattern,
                            coverageAppEnvIds = envIds,
                        )
                    }
                }
            }

            val sortingFieldMapping = mapOf(
                "coverageRatio" to "isolated_probes_coverage_ratio",
                "probesCount" to "probes_count",
                "coveredProbes" to "isolated_covered_probes",
            )
            val buildMappedSortBy = sortBy?.let { sortingFieldMapping[it] }

            return@transaction pagedListOf(
                page = page ?: 1,
                pageSize = pageSize ?: metricsConfig.pageSize
            ) { offset, limit ->
                metricsRepository.getMethodsWithCoverage(
                    buildId = resolvedBuildId,
                    coverageTestTags = testTags,
                    coverageAppEnvIds = envIds,
                    coverageBranches = branches,
                    coverageTestResults = testResults,
                    packageName = packageFilter,
                    className = classFilter,
                    sortBy = buildMappedSortBy,
                    sortOrder = sortOrder,
                    offset = offset,
                    limit = limit
                ).map(::mapToMethodView)
            } withTotal {
                metricsRepository.getMethodsCount(
                    buildId = resolvedBuildId,
                    packageNamePattern = packageFilter,
                    classNamePattern = classFilter,
                )
            }
        }
        return PagedList(result.page, result.pageSize, result.items, result.total, freshness)
    }

    override suspend fun getCoverageByPackage(
        buildId: String,
        testTags: List<String>,
        testResults: List<String>,
        envIds: List<String>,
        branches: List<String>,
    ): List<PackageCoverageView> = transaction {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
        metricsRepository.getPackageCoverage(
            buildId = buildId,
            coverageTestTags = testTags,
            coverageAppEnvIds = envIds,
            coverageBranches = branches,
            coverageTestResults = testResults,
        ).map(::mapToPackageCoverageView)
    }

    override suspend fun getCoverageByClass(
        buildId: String,
        packageName: String?,
        testTags: List<String>,
        testResults: List<String>,
        envIds: List<String>,
        branches: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
        testSessionId: String?,
        testDefinitionId: String?,
    ): PagedList<ClassCoverageView> = transaction {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }

        val packageFilter = packageName?.takeIf { it.isNotBlank() }

        val sortingFieldMapping = mapOf(
            "methodsCoverageRatio" to "methods_coverage_ratio",
            "methodsCount" to "methods_count",
            "coveredMethods" to "covered_methods",
            "probesCoverageRatio" to "probes_coverage_ratio",
            "probesCount" to "probes_count",
            "coveredProbes" to "covered_probes",
        )
        val mappedSortBy = sortBy?.let { sortingFieldMapping[it] }

        when {
            testDefinitionId != null -> {
                val resolvedTestSessionId = testSessionId
                    ?: throw IllegalArgumentException("testSessionId is required when testDefinitionId is specified")
                validateTestSessionBuildForCoverage(resolvedTestSessionId, buildId)

                return@transaction pagedListOf(
                    page = page ?: 1,
                    pageSize = pageSize ?: metricsConfig.pageSize
                ) { offset, limit ->
                    metricsRepository.getClassCoverageByTestDefinition(
                        buildId = buildId,
                        testSessionId = resolvedTestSessionId,
                        testDefinitionId = testDefinitionId,
                        packageName = packageFilter,
                        coverageAppEnvIds = envIds,
                        sortBy = mappedSortBy,
                        sortOrder = sortOrder,
                        offset = offset,
                        limit = limit,
                    ).map(::mapToClassCoverageView)
                } withTotal {
                    metricsRepository.getClassCoverageByTestDefinitionCount(
                        buildId = buildId,
                        testSessionId = resolvedTestSessionId,
                        testDefinitionId = testDefinitionId,
                        packageName = packageFilter,
                        coverageAppEnvIds = envIds,
                    )
                }
            }
            testSessionId != null -> {
                validateTestSessionBuildForCoverage(testSessionId, buildId)

                return@transaction pagedListOf(
                    page = page ?: 1,
                    pageSize = pageSize ?: metricsConfig.pageSize
                ) { offset, limit ->
                    metricsRepository.getClassCoverageByTestSession(
                        buildId = buildId,
                        testSessionId = testSessionId,
                        packageName = packageFilter,
                        testTags = testTags,
                        coverageAppEnvIds = envIds,
                        sortBy = mappedSortBy,
                        sortOrder = sortOrder,
                        offset = offset,
                        limit = limit,
                    ).map(::mapToClassCoverageView)
                } withTotal {
                    metricsRepository.getClassCoverageByTestSessionCount(
                        buildId = buildId,
                        testSessionId = testSessionId,
                        packageName = packageFilter,
                        testTags = testTags,
                        coverageAppEnvIds = envIds,
                    )
                }
            }
        }

        return@transaction pagedListOf(
            page = page ?: 1,
            pageSize = pageSize ?: metricsConfig.pageSize
        ) { offset, limit ->
            metricsRepository.getClassCoverage(
                buildId = buildId,
                packageName = packageFilter,
                coverageTestTags = testTags,
                coverageAppEnvIds = envIds,
                coverageBranches = branches,
                coverageTestResults = testResults,
                sortBy = mappedSortBy,
                sortOrder = sortOrder,
                offset = offset,
                limit = limit,
            ).map(::mapToClassCoverageView)
        } withTotal {
            metricsRepository.getClassCoverageCount(
                buildId = buildId,
                packageName = packageFilter,
                coverageTestTags = testTags,
                coverageAppEnvIds = envIds,
                coverageBranches = branches,
                coverageTestResults = testResults,
            )
        }
    }

    override suspend fun getImpactedTests(
        build: Build,
        baselineBuild: BaselineBuild,
        testCriteria: TestCriteria,
        methodCriteria: MethodCriteria,
        coverageCriteria: CoverageCriteria,
        impactStatuses: List<TestImpactStatus>,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant?
    ): PagedList<TestView> {
        val targetBuildId = build.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Target build info not found for ${build.id}")

        val baselineBuildId = baselineBuild.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Baseline build info not found for ${baselineBuild.id}")

        val mappedSortBy = validateImpactedTestsSortBy(sortBy)

        return pagedFreshListOf(build.groupId, page, pageSize, freshAfter) { offset, limit ->
            metricsRepository.getImpactedTests(
                targetBuildId = targetBuildId,
                baselineBuildId = baselineBuildId,

                testTaskId = testCriteria.testTaskId,
                testTags = testCriteria.testTags,
                testPathPattern = testCriteria.testPath,
                testNamePattern = testCriteria.testName,
                testRunner = testCriteria.testRunner,
                testDefinitionId = testCriteria.testDefinitionId,

                packageNamePattern = methodCriteria.packageNamePattern,
                methodSignaturePattern = methodCriteria.signaturePattern,
                excludeMethodSignatures = methodCriteria.excludeMethodSignatures,

                coverageBranches = coverageCriteria.branches,
                coverageAppEnvIds = coverageCriteria.appEnvIds,

                impactStatuses = impactStatuses,

                sortBy = mappedSortBy,
                sortOrder = sortOrder,

                offset = offset,
                limit = limit,
            ).map { data ->
                TestView(
                    testDefinitionId = data["test_definition_id"] as String,
                    testPath = data["test_path"] as String,
                    testName = data["test_name"] as String,
                    testRunner = data["test_runner"] as String?,
                    testTaskId = data["test_task_id"] as String?,
                    tags = data["test_tags"] as List<String>?,
                    metadata = data["test_metadata"] as JsonElement?,
                    impactStatus = (data["impact_status"] as String).let { TestImpactStatus.valueOf(it) },
                    impactedMethods = (data["impacted_methods"] as Number?)?.toInt(),
                )
            }
        } withTotal {
            metricsRepository.getImpactedTestsCount(
                targetBuildId = targetBuildId,
                baselineBuildId = baselineBuildId,

                testTaskId = testCriteria.testTaskId,
                testTags = testCriteria.testTags,
                testPathPattern = testCriteria.testPath,
                testNamePattern = testCriteria.testName,
                testRunner = testCriteria.testRunner,
                testDefinitionId = testCriteria.testDefinitionId,

                packageNamePattern = methodCriteria.packageNamePattern,
                methodSignaturePattern = methodCriteria.signaturePattern,
                excludeMethodSignatures = methodCriteria.excludeMethodSignatures,

                coverageBranches = coverageCriteria.branches,
                coverageAppEnvIds = coverageCriteria.appEnvIds,

                impactStatuses = impactStatuses,
            )
        }
    }

    override suspend fun getImpactedTestsFilterOptions(
        build: Build,
        baselineBuild: BaselineBuild,
        methodCriteria: MethodCriteria,
        coverageCriteria: CoverageCriteria,
    ): ImpactedTestsFilterOptionsView = transaction {
        val targetBuildId = build.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Target build info not found for ${build.id}")
        val baselineBuildId = baselineBuild.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Baseline build info not found for ${baselineBuild.id}")

        val options = metricsRepository.getImpactedTestsFilterOptions(
            targetBuildId = targetBuildId,
            baselineBuildId = baselineBuildId,
            packageNamePattern = methodCriteria.packageNamePattern,
            methodSignaturePattern = methodCriteria.signaturePattern,
            excludeMethodSignatures = methodCriteria.excludeMethodSignatures,
            coverageBranches = coverageCriteria.branches,
            coverageAppEnvIds = coverageCriteria.appEnvIds,
        )
        ImpactedTestsFilterOptionsView(
            testPaths = options["testPaths"].orEmpty(),
            testNames = options["testNames"].orEmpty(),
            testRunners = options["testRunners"].orEmpty(),
            testTags = options["testTags"].orEmpty(),
            testTaskIds = options["testTaskIds"].orEmpty(),
        )
    }

    override suspend fun getImpactedMethods(
        build: Build,
        baselineBuild: BaselineBuild,
        testCriteria: TestCriteria,
        methodCriteria: MethodCriteria,
        coverageCriteria: CoverageCriteria,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant?,
    ): PagedList<MethodView> {
        val targetBuildId = build.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Target build info not found for ${build.id}")

        val baselineBuildId = baselineBuild.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Baseline build info not found for ${baselineBuild.id}")

        val mappedSortBy = validateImpactedMethodsSortBy(sortBy)

        return pagedFreshListOf(build.groupId, page, pageSize, freshAfter) { offset, limit ->
            metricsRepository.getImpactedMethods(
                targetBuildId = targetBuildId,
                baselineBuildId = baselineBuildId,

                testTaskId = testCriteria.testTaskId,
                testTags = testCriteria.testTags,
                testPathPattern = testCriteria.testPath,
                testNamePattern = testCriteria.testName,

                packageNamePattern = methodCriteria.packageNamePattern,
                methodSignaturePattern = methodCriteria.signaturePattern,
                excludeMethodSignatures = methodCriteria.excludeMethodSignatures,

                coverageBranches = coverageCriteria.branches,
                coverageAppEnvIds = coverageCriteria.appEnvIds,

                sortBy = mappedSortBy,
                sortOrder = sortOrder,

                offset = offset,
                limit = limit
            ).map(::mapToImpactedMethodView)
        }
    }

    private fun mapToBuildDetailView(row: Map<String, Any?>): BuildDetailView = BuildDetailView(
        groupId = row["group_id"] as String,
        appId = row["app_id"] as String,
        buildId = row["build_id"] as String,
        versionId = row["version_id"] as? String,
        buildVersion = row["build_version"] as? String,
        branch = row["branch"] as? String,
        commitSha = row["commit_sha"] as? String,
        commitAuthor = row["commit_author"] as? String,
        commitMessage = row["commit_message"] as? String,
        committedAt = (row["committed_at"] as LocalDateTime?)?.toKotlinLocalDateTime(),
        appEnvIds = (row["app_env_ids"] as List<String>?) ?: emptyList(),
        totalClasses = (row["total_classes"] as? Number)?.toInt() ?: 0,
        totalMethods = (row["total_methods"] as? Number)?.toInt() ?: 0,
        totalProbes = (row["total_probes"] as? Number)?.toInt() ?: 0,
    )

    private fun mapToCoverageUnitSlices(
        row: Map<String, Any?>?,
        unit: CoverageUnit,
    ): List<CoverageUnitSliceView> {
        if (row == null) {
            return coverageUnitSlices(0, 0, 0)
        }
        return when (unit) {
            CoverageUnit.PROBES -> coverageUnitSlices(
                total = (row["total_probes"] as? Number)?.toInt() ?: 0,
                isolatedCovered = (row["isolated_covered_probes"] as? Number)?.toInt() ?: 0,
                aggregatedCovered = (row["aggregated_covered_probes"] as? Number)?.toInt() ?: 0,
            )
            CoverageUnit.METHODS -> coverageUnitSlices(
                total = (row["total_methods"] as? Number)?.toInt() ?: 0,
                isolatedCovered = (row["isolated_tested_methods"] as? Number)?.toInt() ?: 0,
                aggregatedCovered = (row["aggregated_tested_methods"] as? Number)?.toInt() ?: 0,
            )
        }
    }

    private fun coverageUnitSlices(
        total: Int,
        isolatedCovered: Int,
        aggregatedCovered: Int,
    ): List<CoverageUnitSliceView> {
        val coveredInOtherBuilds = (aggregatedCovered - isolatedCovered).coerceAtLeast(0)
        val gaps = (total - aggregatedCovered).coerceAtLeast(0)
        return listOf(
            CoverageUnitSliceView(metric = "covered", value = isolatedCovered),
            CoverageUnitSliceView(metric = "covered_in_other_builds", value = coveredInOtherBuilds),
            CoverageUnitSliceView(metric = "gaps", value = gaps),
        )
    }

    private fun mapToPackageCoverageView(row: Map<String, Any?>): PackageCoverageView {
        val methodsCount = (row["methods_count"] as? Number)?.toInt() ?: 0
        val coveredMethods = (row["covered_methods"] as? Number)?.toInt() ?: 0
        val probesCount = (row["probes_count"] as? Number)?.toInt() ?: 0
        val coveredProbes = (row["covered_probes"] as? Number)?.toInt() ?: 0
        val coveredProbesInOtherBuilds =
            (row["aggregated_covered_probes"] as Number).toInt()
        return PackageCoverageView(
            packageName = row["package_name"] as? String ?: "",
            methodsCount = methodsCount,
            coveredMethods = coveredMethods,
            coveredMethodsInOtherBuilds =
                (row["aggregated_covered_methods"] as Number).toInt(),
            missedMethods = (row["missed_methods"] as? Number)?.toInt() ?: 0,
            probesCount = probesCount,
            coveredProbes = coveredProbes,
            coveredProbesInOtherBuilds = coveredProbesInOtherBuilds,
            missedProbes = (row["missed_probes"] as? Number)?.toInt() ?: 0,
            probesCoverageRatio = coverageRatio(coveredProbes, probesCount),
            methodsCoverageRatio = coverageRatio(coveredMethods, methodsCount),
        )
    }

    private fun mapToClassCoverageView(row: Map<String, Any?>): ClassCoverageView {
        val methodsCount = (row["methods_count"] as? Number)?.toInt() ?: 0
        val coveredMethods = (row["covered_methods"] as? Number)?.toInt() ?: 0
        val probesCount = (row["probes_count"] as? Number)?.toInt() ?: 0
        val coveredProbes = (row["covered_probes"] as? Number)?.toInt() ?: 0
        val coveredProbesInOtherBuilds =
            (row["aggregated_covered_probes"] as Number).toInt()
        val fullClassName = row["class_name"] as String
        return ClassCoverageView(
            fullClassName = fullClassName,
            packageName = packageNameFromClassName(fullClassName),
            className = simpleClassName(fullClassName),
            methodsCount = methodsCount,
            coveredMethods = coveredMethods,
            coveredMethodsInOtherBuilds =
                (row["aggregated_covered_methods"] as Number).toInt(),
            missedMethods = (row["missed_methods"] as? Number)?.toInt() ?: 0,
            probesCount = probesCount,
            coveredProbes = coveredProbes,
            coveredProbesInOtherBuilds = coveredProbesInOtherBuilds,
            missedProbes = (row["missed_probes"] as? Number)?.toInt() ?: 0,
            probesCoverageRatio = coverageRatio(coveredProbes, probesCount),
            methodsCoverageRatio = coverageRatio(coveredMethods, methodsCount),
        )
    }

    private fun coverageRatio(covered: Int, total: Int): Double =
        if (total > 0) covered.toDouble() / total else 0.0

    private fun ratioToPercent(value: Any?): Double =
        ((value as? Number)?.toDouble() ?: 0.0) * 100.0

    private fun normalizeTrendSize(size: Int?): Int =
        (size ?: DEFAULT_TREND_SIZE).coerceIn(1, MAX_TREND_SIZE)

    private fun mapToBuildChangeView(resultSet: Map<String, Any?>): BuildChangeView = BuildChangeView(
        signature = resultSet["signature"] as String,
        className = resultSet["class_name"] as String,
        name = resultSet["method_name"] as String,
        params = (resultSet["method_params"] as String).split(",").map(String::trim),
        returnType = resultSet["return_type"] as String,
        changeType = ChangeType.fromString(resultSet["change_type"] as String?),
        probesCount = (resultSet["probes_count"] as Number?)?.toInt(),
        coveredProbes = (resultSet["isolated_covered_probes"] as Number?)?.toInt(),
        coveredProbesInOtherBuilds = (resultSet["aggregated_covered_probes"] as Number?)?.toInt(),
        coverageRatio = (resultSet["isolated_probes_coverage_ratio"] as Number?)?.toDouble(),
        coverageRatioInOtherBuilds = (resultSet["aggregated_probes_coverage_ratio"] as Number?)?.toDouble(),
        missedProbes = (resultSet["isolated_missed_probes"] as Number?)?.toInt(),
        missedProbesInOtherBuilds = (resultSet["aggregated_missed_probes"] as Number?)?.toInt(),
        impactedTests = (resultSet["impacted_tests"] as Number).toInt(),
    )

    private fun mapToImpactedMethodView(resultSet: Map<String, Any?>): MethodView = MethodView(
        signature = resultSet["signature"] as String,
        className = resultSet["class_name"] as String,
        name = resultSet["method_name"] as String,
        params = (resultSet["method_params"] as? String)?.split(",")?.map(String::trim) ?: emptyList(),
        returnType = resultSet["return_type"] as? String ?: "",
        changeType = ChangeType.fromString(resultSet["change_type"] as String?),
        probesCount = (resultSet["probes_count"] as Number?)?.toInt() ?: 0,
        coveredProbes = (resultSet["isolated_covered_probes"] as Number?)?.toInt() ?: 0,
        coveredProbesInOtherBuilds = (resultSet["aggregated_covered_probes"] as Number?)?.toInt() ?: 0,
        coverageRatio = (resultSet["isolated_probes_coverage_ratio"] as Number?)?.toDouble() ?: 0.0,
        coverageRatioInOtherBuilds = (resultSet["aggregated_probes_coverage_ratio"] as Number?)?.toDouble() ?: 0.0,
        impactedTests = (resultSet["impacted_tests"] as Number?)?.toInt(),
    )

    private fun mapToMethodView(resultSet: Map<String, Any?>): MethodView = MethodView(
        methodId = resultSet["method_id"] as? String,
        signature = resultSet["signature"] as String,
        className = resultSet["class_name"] as String,
        name = resultSet["method_name"] as String,
        params = (resultSet["method_params"] as String).split(",").map(String::trim),
        returnType = resultSet["return_type"] as String,
        changeType = ChangeType.fromString(resultSet["change_type"] as String?),
        probesCount = (resultSet["probes_count"] as Number?)?.toInt() ?: 0,
        coveredProbes = (resultSet["isolated_covered_probes"] as Number?)?.toInt() ?: 0,
        coveredProbesInOtherBuilds = (resultSet["aggregated_covered_probes"] as Number?)?.toInt() ?: 0,
        coverageRatio = (resultSet["isolated_probes_coverage_ratio"] as Number?)?.toDouble() ?: 0.0,
        coverageRatioInOtherBuilds = (resultSet["aggregated_probes_coverage_ratio"] as Number?)?.toDouble() ?: 0.0,
        missedProbes = (resultSet["isolated_missed_probes"] as Number?)?.toInt(),
        missedProbesInOtherBuilds = (resultSet["aggregated_missed_probes"] as Number?)?.toInt(),
        impactedTests = (resultSet["impacted_tests"] as Number?)?.toInt(),
    )

    private fun mapToTestSessionCoverageSummaryView(row: Map<String, Any?>?): TestSessionCoverageSummaryView {
        val coveredProbes = (row?.get("covered_probes") as? Number)?.toInt() ?: 0
        val missedProbes = (row?.get("missed_probes") as? Number)?.toInt() ?: 0
        val testedMethods = (row?.get("tested_methods") as? Number)?.toInt() ?: 0
        val missedMethods = (row?.get("missed_methods") as? Number)?.toInt() ?: 0
        return TestSessionCoverageSummaryView(
            probes = CoverageUnitSummaryView(
                slices = listOf(
                    CoverageUnitSliceView(metric = "covered", value = coveredProbes),
                    CoverageUnitSliceView(metric = "missed", value = missedProbes),
                ),
            ),
            methods = CoverageUnitSummaryView(
                slices = listOf(
                    CoverageUnitSliceView(metric = "covered", value = testedMethods),
                    CoverageUnitSliceView(metric = "missed", value = missedMethods),
                ),
            ),
        )
    }

    private fun pageFromRowNumber(rowNumber: Long, pageSize: Int?): Int {
        val size = pageSize?.takeIf { it > 0 } ?: metricsConfig.pageSize
        return ((rowNumber - 1) / size).toInt() + 1
    }

    private suspend fun pagedStringList(
        page: Int?,
        pageSize: Int?,
        getItems: suspend (Int, Int) -> List<String>,
        getTotal: suspend () -> Long,
    ): PagedList<String> =
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            getItems(offset, limit)
        } withTotal { getTotal() }

    private fun validateTestSessionFilterField(field: String): String {
        val normalized = field.trim()
        if (normalized.isBlank() || normalized !in TEST_SESSION_FILTER_FIELDS) {
            throw IllegalArgumentException(
                "Invalid field '$field'. Allowed values: ${TEST_SESSION_FILTER_FIELDS.joinToString(", ")}"
            )
        }
        return normalized
    }

    private fun validateBuildChangeSortBy(sortBy: String?): String? = sortBy?.let { requestedSortBy ->
        val normalized = requestedSortBy.trim()
        if (normalized.isBlank() || normalized !in BUILD_CHANGE_SORT_FIELDS) {
            throw IllegalArgumentException(
                "Invalid sortBy '$requestedSortBy'. Allowed values: ${BUILD_CHANGE_SORT_FIELDS.joinToString(", ")}"
            )
        }
        normalized
    }

    private fun validateTestSessionSortBy(sortBy: String?): String? = sortBy?.let { requestedSortBy ->
        val normalized = requestedSortBy.trim()
        if (normalized.isBlank() || normalized !in TEST_SESSION_SORT_FIELDS) {
            throw IllegalArgumentException(
                "Invalid sortBy '$requestedSortBy'. Allowed values: ${TEST_SESSION_SORT_FIELDS.joinToString(", ")}"
            )
        }
        normalized
    }

    private fun validateImpactedTestsSortBy(sortBy: String?): String? = sortBy?.let { requestedSortBy ->
        val normalized = requestedSortBy.trim()
        IMPACTED_TESTS_SORT_FIELDS[normalized]
            ?: throw IllegalArgumentException(
                "Invalid sortBy '$requestedSortBy'. Allowed values: ${IMPACTED_TESTS_SORT_FIELDS.keys.joinToString(", ")}"
            )
    }

    private fun validateImpactedMethodsSortBy(sortBy: String?): String? = sortBy?.let { requestedSortBy ->
        val normalized = requestedSortBy.trim()
        IMPACTED_METHODS_SORT_FIELDS[normalized]
            ?: throw IllegalArgumentException(
                "Invalid sortBy '$requestedSortBy'. Allowed values: ${IMPACTED_METHODS_SORT_FIELDS.keys.joinToString(", ")}"
            )
    }

    private fun validateTestFileLaunchSortBy(sortBy: String?): String? = sortBy?.let { requestedSortBy ->
        val normalized = requestedSortBy.trim()
        if (normalized.isBlank() || normalized !in TEST_FILE_LAUNCH_SORT_FIELDS) {
            throw IllegalArgumentException(
                "Invalid sortBy '$requestedSortBy'. Allowed values: ${TEST_FILE_LAUNCH_SORT_FIELDS.joinToString(", ")}"
            )
        }
        normalized
    }

    private fun validateTestLaunchSortBy(sortBy: String?): String? = sortBy?.let { requestedSortBy ->
        val normalized = requestedSortBy.trim()
        if (normalized.isBlank() || normalized !in TEST_LAUNCH_SORT_FIELDS) {
            throw IllegalArgumentException(
                "Invalid sortBy '$requestedSortBy'. Allowed values: ${TEST_LAUNCH_SORT_FIELDS.joinToString(", ")}"
            )
        }
        normalized
    }

    private fun mapToTestSessionView(row: Map<String, Any?>): TestSessionView = TestSessionView(
        testSessionId = row["test_session_id"] as String,
        groupId = row["group_id"] as String,
        appId = row["app_id"] as? String,
        buildId = row["build_id"] as? String,
        testTaskId = row["test_task_id"] as String?,
        sessionStartedAt = (row["session_started_at"] as LocalDateTime?)?.toKotlinLocalDateTime(),
        createdBy = row["created_by"] as String?,
        testDefinitions = (row["test_definitions"] as? Number)?.toInt() ?: 0,
        testLaunches = (row["test_launches"] as? Number)?.toInt() ?: 0,
        result = row["result"] as String,
        testDuration = (row["test_duration"] as? Number)?.toLong() ?: 0L,
        testDurationFormatted = row["test_duration_formatted"] as String,
        failed = (row["failed"] as? Number)?.toInt() ?: 0,
        passed = (row["passed"] as? Number)?.toInt() ?: 0,
        skipped = (row["skipped"] as? Number)?.toInt() ?: 0,
        smartSkipped = (row["smart_skipped"] as? Number)?.toInt() ?: 0,
        success = (row["success"] as? Number)?.toInt() ?: 0,
        successRate = (row["success_rate"] as? Number)?.toDouble() ?: 0.0,
        timeSaved = (row["time_saved"] as? Number)?.toLong() ?: 0L,
        timeSavedFormatted = row["time_saved_formatted"] as String,
    )

    private fun mapToTestSessionDetailView(row: Map<String, Any?>): TestSessionDetailView = TestSessionDetailView(
        testSessionId = row["test_session_id"] as String,
        groupId = row["group_id"] as String,
        appId = row["app_id"] as String,
        buildId = row["build_id"] as String,
        buildVersion = row["build_version"] as? String,
        branch = row["branch"] as? String,
        testTaskId = row["test_task_id"] as String?,
        sessionStartedAt = (row["session_started_at"] as LocalDateTime?)?.toKotlinLocalDateTime(),
        createdBy = row["created_by"] as String?,
        testDefinitions = (row["test_definitions"] as? Number)?.toInt() ?: 0,
        testLaunches = (row["test_launches"] as? Number)?.toInt() ?: 0,
        result = row["result"] as String,
        testDuration = (row["test_duration"] as? Number)?.toLong() ?: 0L,
        testDurationFormatted = row["test_duration_formatted"] as String,
        failed = (row["failed"] as? Number)?.toInt() ?: 0,
        passed = (row["passed"] as? Number)?.toInt() ?: 0,
        skipped = (row["skipped"] as? Number)?.toInt() ?: 0,
        smartSkipped = (row["smart_skipped"] as? Number)?.toInt() ?: 0,
        success = (row["success"] as? Number)?.toInt() ?: 0,
        successRate = (row["success_rate"] as? Number)?.toDouble() ?: 0.0,
        timeSaved = (row["time_saved"] as? Number)?.toLong() ?: 0L,
        timeSavedFormatted = row["time_saved_formatted"] as String,
    )

    private suspend fun validateTestSessionBuild(groupId: String, testSessionId: String, buildId: String) {
        if (!metricsRepository.testSessionExists(groupId, testSessionId)) {
            throw ResourceNotFoundException("Test session not found for $testSessionId in group $groupId")
        }
        if (!metricsRepository.testSessionBuildExists(groupId, testSessionId, buildId)) {
            throw ResourceNotFoundException("Test session $testSessionId is not linked to build $buildId")
        }
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
    }

    private suspend fun validateTestSessionBuildForCoverage(testSessionId: String, buildId: String) {
        val groupId = buildId.substringBefore(":")
        validateTestSessionBuild(groupId, testSessionId, buildId)
    }

    private fun mapToTestLaunchView(row: Map<String, Any?>): TestLaunchView = TestLaunchView(
        testDefinitionId = row["test_definition_id"] as String,
        testName = row["test_name"] as? String,
        testPath = row["test_path"] as? String,
        testRunner = row["test_runner"] as? String,
        testTags = (row["test_tags"] as? List<String>) ?: emptyList(),
        testLaunches = (row["test_launches"] as? Number)?.toInt() ?: 0,
        testDuration = (row["test_duration"] as? Number)?.toLong() ?: 0L,
        testDurationFormatted = row["test_duration_formatted"] as String,
        testResult = row["test_result"] as String,
    )

    private fun mapToTestFileLaunchView(row: Map<String, Any?>): TestFileLaunchView = TestFileLaunchView(
        testPath = row["test_path"] as String,
        testDefinitions = (row["test_definitions"] as? Number)?.toInt() ?: 0,
        testLaunches = (row["test_launches"] as? Number)?.toInt() ?: 0,
        result = row["result"] as String,
        failed = (row["failed"] as? Number)?.toInt() ?: 0,
        passed = (row["passed"] as? Number)?.toInt() ?: 0,
        skipped = (row["skipped"] as? Number)?.toInt() ?: 0,
        smartSkipped = (row["smart_skipped"] as? Number)?.toInt() ?: 0,
        success = (row["success"] as? Number)?.toInt() ?: 0,
        testDuration = (row["test_duration"] as? Number)?.toLong() ?: 0L,
        testDurationFormatted = row["test_duration_formatted"] as String,
        successRate = (row["success_rate"] as? Number)?.toDouble() ?: 0.0,
    )

    // TODO good candidate to be moved to common functions (probably)
    private fun getUriString(baseUrl: String, path: String, queryParams: Map<String, String>): String {
        val uri = URI(baseUrl).resolve(path)
        val queryString = queryParams.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8.toString())}"
        }
        return URI("$uri?$queryString").toString()
    }

    private suspend fun refresh(groupId: String, freshAfter: Instant?): Instant? {
        return freshAfter?.let {
            etlService.forceRefresh(groupId, snapshotTimestamp = freshAfter)
        }
    }

    /**
     * Fetches paged list of items with optional freshness guarantee.
     * If [freshAfter] is provided, it will trigger a refresh and wait for it to complete before fetching the items.
     */
    private suspend fun <T> pagedFreshListOf(
        groupId: String,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant?,
        getItems: suspend (offset: Int, limit: Int) -> List<T>
    ): PagedList<T> {
        val page = page ?: 1
        val pageSize = pageSize ?: metricsConfig.pageSize
        val freshness = refresh(groupId, freshAfter)
        val items = getItems((page - 1) * pageSize, pageSize)
        return PagedList(
            page, pageSize, items, when {
                items.size < pageSize -> ((page - 1) * pageSize + items.size).toLong()
                else -> null
            },
            refreshedAt = freshness
        )
    }

    companion object {
        private const val DEFAULT_TREND_SIZE = 100
        private const val MAX_TREND_SIZE = 500
        private val BUILD_CHANGE_SORT_FIELDS = setOf(
            "changeType",
            "coverageRatioInOtherBuilds",
            "impactedTests",
            "aggregatedMissedProbes",
            "signature",
        )
        private val TEST_SESSION_SORT_FIELDS = setOf("sessionStartedAt", "successRate")
        private val TEST_SESSION_FILTER_FIELDS = setOf("testTaskIds", "createdBys", "results")
        private val TEST_FILE_LAUNCH_SORT_FIELDS = setOf(
            "testDefinitions",
            "testLaunches",
            "passed",
            "failed",
            "skipped",
            "smartSkipped",
            "testDuration",
            "successRate",
        )
        private val TEST_LAUNCH_SORT_FIELDS = setOf("testLaunches", "testDuration")
        private val IMPACTED_TESTS_SORT_FIELDS = mapOf(
            "testPath" to "test_path",
            "testName" to "test_name",
            "testRunner" to "test_runner",
            "impactedMethods" to "impacted_methods",
        )
        private val IMPACTED_METHODS_SORT_FIELDS = mapOf(
            "signature" to "signature",
            "className" to "class_name",
            "name" to "method_name",
            "impactedTests" to "impacted_tests",
        )
    }
}
