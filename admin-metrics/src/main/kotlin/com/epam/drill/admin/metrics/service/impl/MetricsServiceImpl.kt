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
import com.epam.drill.admin.metrics.config.MetricsConfig
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig.transaction
import com.epam.drill.admin.metrics.config.MetricsServiceUiLinksConfig
import com.epam.drill.admin.metrics.config.TestRecommendationsConfig
import com.epam.drill.admin.metrics.models.BaselineBuild
import com.epam.drill.admin.metrics.models.Build
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
import java.time.LocalDateTime

class MetricsServiceImpl(
    private val metricsRepository: MetricsRepository,
    private val metricsServiceUiLinksConfig: MetricsServiceUiLinksConfig,
    private val testRecommendationsConfig: TestRecommendationsConfig,
    private val metricsConfig: MetricsConfig,
) : MetricsService {

    private val logger = KotlinLogging.logger {}

    override suspend fun getGroups(): List<String> = transaction {
        metricsRepository.getGroups()
    }

    override suspend fun getApplications(groupId: String?): List<ApplicationView> {
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
        page: Int?,
        pageSize: Int?
    ): PagedList<BuildView> = transaction {
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getBuilds(
                groupId, appId,
                branches, envIds,
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
                branches, envIds
            )
        }
    }

    override suspend fun getAppBranches(groupId: String, appId: String): List<String> = transaction {
        metricsRepository.getAppBranches(groupId, appId)
    }

    override suspend fun getAppEnvIds(groupId: String, appId: String): List<String> = transaction {
        metricsRepository.getAppEnvIds(groupId, appId)
    }

    override suspend fun getAppTestTags(groupId: String, appId: String): List<String> = transaction {
        metricsRepository.getAppTestTags(groupId, appId)
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
    ): CoverageUnitSummaryView = transaction {
        getBuildCoverageUnitSummary(buildId, baselineBuildId, envIds, branches, testTags, CoverageUnit.PROBES)
    }

    override suspend fun getBuildCoverageByMethods(
        buildId: String,
        baselineBuildId: String?,
        envIds: List<String>,
        branches: List<String>,
        testTags: List<String>,
    ): CoverageUnitSummaryView = transaction {
        getBuildCoverageUnitSummary(buildId, baselineBuildId, envIds, branches, testTags, CoverageUnit.METHODS)
    }

    private suspend fun getBuildCoverageUnitSummary(
        buildId: String,
        baselineBuildId: String?,
        envIds: List<String>,
        branches: List<String>,
        testTags: List<String>,
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
            buildId, baselineBuildId, envIds, branches, testTags
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
        ChangesSummaryView(
            modifiedMethods = (row["modified_methods"] as? Number)?.toInt() ?: 0,
            newMethods = (row["new_methods"] as? Number)?.toInt() ?: 0,
            deletedMethods = (row["deleted_methods"] as? Number)?.toInt() ?: 0,
        )
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
    ): TestSessionFilterOptionsView = transaction {
        TestSessionFilterOptionsView(
            testTaskIds = metricsRepository.getTestSessionTestTaskIds(groupId, buildId),
            createdBys = metricsRepository.getTestSessionCreatedBys(groupId, buildId),
            results = metricsRepository.getTestSessionResults(groupId, buildId),
        )
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
    ): TestSessionCoverageSummaryView = transaction {
        validateTestSessionBuild(groupId, testSessionId, buildId)
        val row = if (testDefinitionId.isNullOrBlank()) {
            metricsRepository.getTestSessionCoverageSummary(buildId, testSessionId)
        } else {
            metricsRepository.getTestDefinitionCoverageSummary(buildId, testSessionId, testDefinitionId)
        }
        mapToTestSessionCoverageSummaryView(row)
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
        testResults: List<String>,
        testTags: List<String>,
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
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getTestLaunches(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
                path = path,
                testResults = testResults,
                testTags = testTags,
                offset = offset,
                limit = limit,
            ).map { row -> mapToTestLaunchView(row) }
        } withTotal {
            metricsRepository.getTestLaunchesCount(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
                path = path,
                testResults = testResults,
                testTags = testTags,
            )
        }
    }

    override suspend fun getTestFileLaunches(
        groupId: String,
        testSessionId: String,
        buildId: String?,
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
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getTestFileLaunches(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
                offset = offset,
                limit = limit,
            ).map { row -> mapToTestFileLaunchView(row) }
        } withTotal {
            metricsRepository.getTestFileLaunchesCount(
                groupId = groupId,
                testSessionId = testSessionId,
                buildId = buildId,
            )
        }
    }

    override suspend fun getCoverageTreemap(
        buildId: String,
        testTags: List<String>,
        envIds: List<String>,
        branches: List<String>,
        packageNamePattern: String?,
        classNamePattern: String?,
        rootId: String?,
        testSessionId: String?,
        testDefinitionId: String?,
    ): List<Any> {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
        val methodCriteria = MethodCriteria(
            packageName = packageNamePattern,
            className = classNamePattern
        )

        val data = when {
            testDefinitionId != null -> {
                val resolvedTestSessionId = testSessionId
                    ?: throw IllegalArgumentException("testSessionId is required when testDefinitionId is specified")
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
                    packageName = packageNamePattern?.takeIf { it.isNotBlank() },
                    className = classNamePattern?.takeIf { it.isNotBlank() }
                )
            }
        }

        return buildTree(data, rootId)
    }

    override suspend fun getChangesCoverageTreemap(
        buildId: String,
        baselineBuildId: String,
        testTags: List<String>,
        envIds: List<String>,
        branches: List<String>,
        packageNamePattern: String?,
        classNamePattern: String?,
        rootId: String?,
        includeDeleted: Boolean?,
        includeEqual: Boolean?,
    ): List<Any> {

        if (!metricsRepository.buildExists(baselineBuildId)) {
            throw BuildNotFound("Baseline build info not found for $baselineBuildId")
        }

        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }

        val data = metricsRepository.getChangesWithCoverage(
            buildId = buildId,
            baselineBuildId = baselineBuildId,
            coverageTestTags = testTags,
            coverageAppEnvIds = envIds,
            coverageBranches = branches,
            packageName = packageNamePattern?.takeIf { it.isNotBlank() },
            className = classNamePattern?.takeIf { it.isNotBlank() },
            includeDeleted = includeDeleted?.takeIf { it },
            includeEqual = includeEqual?.takeIf { it },
        )

        return buildTree(data, rootId)
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
        envIds: List<String>,
        branches: List<String>,
        changeTypes: List<String>,
        hasImpactedTests: Boolean?,
        methodSignature: String?,
        testDefinitionId: String?,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?
    ): PagedList<BuildChangeView> = transaction {
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

        return@transaction pagedListOf(
            page = page ?: 1,
            pageSize = pageSize ?: metricsConfig.pageSize
        ) { offset, limit ->
            metricsRepository.getBuildChanges(
                buildId = buildId,
                baselineBuildId = baselineBuildId,
                groupId = groupId,
                appId = appId,
                coverageTestTags = testTags,
                coverageAppEnvIds = envIds,
                coverageBranches = branches,
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
    ): PagedList<MethodView> = transaction {
        val resolvedBuildId = buildId?.takeIf { it.isNotBlank() }
            ?: generateBuildId(groupId!!, appId!!, instanceId, commitSha, buildVersion)
        if (!metricsRepository.buildExists(resolvedBuildId)) {
            throw BuildNotFound("Build info not found for $resolvedBuildId")
        }

        val packageFilter = packageNamePattern?.takeIf { it.isNotBlank() }
        val classFilter = classNamePattern?.takeIf { it.isNotBlank() }
        val methodCriteria = MethodCriteria(packageName = packageFilter, className = classFilter)

        val sessionSortMapping = mapOf(
            "coverageRatio" to "probes_coverage_ratio",
            "probesCount" to "probes_count",
            "coveredProbes" to "covered_probes",
        )
        val mappedSortBy = sortBy?.let { sessionSortMapping[it] }

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

    override suspend fun getCoverageByPackage(
        buildId: String,
        testTags: List<String>,
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
        ).map(::mapToPackageCoverageView)
    }

    override suspend fun getCoverageByClass(
        buildId: String,
        packageName: String?,
        testTags: List<String>,
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
            )
        }
    }

    override suspend fun getImpactedTests(
        build: Build,
        baselineBuild: BaselineBuild,
        testCriteria: TestCriteria,
        methodCriteria: MethodCriteria,
        coverageCriteria: CoverageCriteria,
        sortBy: String?,
        sortOrder: SortOrder?,
        page: Int?,
        pageSize: Int?
    ): PagedList<TestView> = transaction {
        val targetBuildId = build.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Target build info not found for ${build.id}")

        val baselineBuildId = baselineBuild.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Baseline build info not found for ${baselineBuild.id}")

        // Map response field names to database column names
        val sortingFieldMapping = mapOf(
            "testPath" to "test_path",
            "testName" to "test_name",
            "testRunner" to "test_runner",
            "impactedMethods" to "impacted_methods"
        )
        val mappedSortBy = sortBy?.let { sortingFieldMapping[it] ?: it }

        return@transaction pagedListOf(
            page = page ?: 1,
            pageSize = pageSize ?: metricsConfig.pageSize
        ) { offset, limit ->
            metricsRepository.getImpactedTests(
                targetBuildId = targetBuildId,
                baselineBuildId = baselineBuildId,

                testTaskId = testCriteria.testTaskId,
                testTags = testCriteria.testTags,
                testPathPattern = testCriteria.testPath,
                testNamePattern = testCriteria.testName,
                testDefinitionId = testCriteria.testDefinitionId,

                packageNamePattern = methodCriteria.packageNamePattern,
                methodSignaturePattern = methodCriteria.signaturePattern,
                excludeMethodSignatures = methodCriteria.excludeMethodSignatures,

                coverageBranches = coverageCriteria.branches,
                coverageAppEnvIds = coverageCriteria.appEnvIds,

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
                    tags = data["test_tags"] as List<String>?,
                    metadata = data["test_metadata"] as JsonElement?,
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
                testDefinitionId = testCriteria.testDefinitionId,

                packageNamePattern = methodCriteria.packageNamePattern,
                methodSignaturePattern = methodCriteria.signaturePattern,
                excludeMethodSignatures = methodCriteria.excludeMethodSignatures,

                coverageBranches = coverageCriteria.branches,
                coverageAppEnvIds = coverageCriteria.appEnvIds,
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
            testTags = options["testTags"].orEmpty(),
        )
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
        return PackageCoverageView(
            packageName = row["package_name"] as? String ?: "",
            methodsCount = methodsCount,
            coveredMethods = coveredMethods,
            missedMethods = (row["missed_methods"] as? Number)?.toInt() ?: 0,
            probesCount = probesCount,
            coveredProbes = coveredProbes,
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
        val fullClassName = row["class_name"] as String
        return ClassCoverageView(
            fullClassName = fullClassName,
            packageName = packageNameFromClassName(fullClassName),
            className = simpleClassName(fullClassName),
            methodsCount = methodsCount,
            coveredMethods = coveredMethods,
            missedMethods = (row["missed_methods"] as? Number)?.toInt() ?: 0,
            probesCount = probesCount,
            coveredProbes = coveredProbes,
            missedProbes = (row["missed_probes"] as? Number)?.toInt() ?: 0,
            probesCoverageRatio = coverageRatio(coveredProbes, probesCount),
            methodsCoverageRatio = coverageRatio(coveredMethods, methodsCount),
        )
    }

    private fun coverageRatio(covered: Int, total: Int): Double =
        if (total > 0) covered.toDouble() / total else 0.0

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

    private fun mapToChangeView(resultSet: Map<String, Any?>): ChangeView = ChangeView(
        signature = resultSet["signature"] as String,
        className = resultSet["class_name"] as String,
        name = resultSet["method_name"] as String,
        params = (resultSet["method_params"] as String).split(",").map(String::trim),
        returnType = resultSet["return_type"] as String,
        changeType = ChangeType.fromString(resultSet["change_type"] as String?),
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

    companion object {
        private val BUILD_CHANGE_SORT_FIELDS = setOf(
            "changeType",
            "coverageRatioInOtherBuilds",
            "impactedTests",
            "aggregatedMissedProbes",
            "signature",
        )
        private val TEST_SESSION_SORT_FIELDS = setOf("sessionStartedAt", "successRate")
    }
}
