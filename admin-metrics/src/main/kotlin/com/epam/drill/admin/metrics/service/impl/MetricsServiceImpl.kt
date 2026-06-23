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

    override suspend fun getRecommendedTests(
        groupId: String,
        appId: String,
        testsToSkip: Boolean?,
        testTaskId: String?,
        coveragePeriodDays: Int?,
        targetInstanceId: String?,
        targetCommitSha: String?,
        targetBuildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
        baselineBuildBranches: List<String>
    ): Map<String, Any?> = transaction {
        val hasBaselineBuild = listOf(baselineInstanceId, baselineCommitSha, baselineBuildVersion).any { it != null }

        val baselineBuildId = takeIf { hasBaselineBuild }?.let {
            generateBuildId(
                groupId,
                appId,
                baselineInstanceId,
                baselineCommitSha,
                baselineBuildVersion,
                """
                Provide at least one the following: baselineInstanceId, baselineCommitSha, baselineBuildVersion
                """.trimIndent()
            ).also { buildId ->
                if (!metricsRepository.buildExists(buildId)) {
                    throw BuildNotFound("Baseline build info not found for $buildId")
                }
            }
        }

        val targetBuildId = generateBuildId(
            groupId,
            appId,
            targetInstanceId,
            targetCommitSha,
            targetBuildVersion,
            """
            Provide at least one the following: targetInstanceId, targetCommitSha, targetBuildVersion
            """.trimIndent()
        )
        if (!metricsRepository.buildExists(targetBuildId)) {
            throw BuildNotFound("Target build info not found for $targetBuildId")
        }

        val coveragePeriodFrom = (coveragePeriodDays ?: testRecommendationsConfig.coveragePeriodDays)?.let {
            LocalDateTime.now().minusDays(it.toLong())
        }
        val testImpactStatus = when (testsToSkip) {
            true -> listOf(TestImpactStatus.NOT_IMPACTED)
            false -> listOf(TestImpactStatus.IMPACTED, TestImpactStatus.UNKNOWN_IMPACT)
            null -> TestImpactStatus.entries
        }


        val recommendedTests = metricsRepository.getRecommendedTests(
            targetBuildId = targetBuildId,
            testImpactStatuses = testImpactStatus.map { it.name },
            baselineUntilBuildId = baselineBuildId,
            baselineBuildBranches = baselineBuildBranches,
            testTaskIds = listOfNotNull(testTaskId),
            coveragePeriodFrom = coveragePeriodFrom,
            offset = 0,
            limit = null
        ).map { data ->
            RecommendedTestsView(
                testDefinitionId = data["test_definition_id"] as String,
                testRunner = data["test_runner"] as String?,
                testPath = data["test_path"] as String,
                testName = data["test_name"] as String,
                tags = data["test_tags"] as List<String>?,
                metadata = data["test_metadata"] as JsonElement?,
                testImpactStatus = (data["test_impact_status"] as String?)?.let { TestImpactStatus.valueOf(it) },
                impactedMethods = (data["impacted_methods"] as Number?)?.toInt(),
                baselineBuildId = data["baseline_build_id"] as String?,
            )
        }

        // TODO add recommended tests UI link
        // val recommendedTestsReportPath = metricsServiceUiLinksConfig.recommendedTestsReportPath
        mapOf(
            "inputParameters" to mapOf(
                "groupId" to groupId,
                "appId" to appId,
                "targetInstanceId" to targetInstanceId,
                "targetCommitSha" to targetCommitSha,
                "targetBuildVersion" to targetBuildVersion,
                "baselineInstanceId" to baselineInstanceId,
                "baselineCommitSha" to baselineCommitSha,
                "baselineBuildVersion" to baselineBuildVersion,
            ),
            "inferredValues" to mapOf(
                "build" to targetBuildId,
                "baselineBuild" to baselineBuildId,
            ),
            "recommendedTests" to recommendedTests,
        )
    }

    override suspend fun getChanges(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
        includeDeleted: Boolean?,
        includeEqual: Boolean?,
        page: Int?,
        pageSize: Int?
    ): PagedList<MethodView> = transaction {
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

        return@transaction pagedListOf(
            page = page ?: 1,
            pageSize = pageSize ?: metricsConfig.pageSize
        ) { offset, limit ->
            metricsRepository.getChangesWithCoverage(
                buildId = buildId,
                baselineBuildId = baselineBuildId,
                includeDeleted = includeDeleted?.takeIf { it },
                includeEqual = includeEqual?.takeIf { it },
                offset = offset,
                limit = limit
            ).map(::mapToMethodView)
        } withTotal {
            metricsRepository.getChangesCount(buildId = buildId, baselineBuildId = baselineBuildId)
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
        page: Int?,
        pageSize: Int?
    ): PagedList<MethodView> = transaction {
        val resolvedBuildId = buildId?.takeIf { it.isNotBlank() }
            ?: generateBuildId(groupId!!, appId!!, instanceId, commitSha, buildVersion)
        if (!metricsRepository.buildExists(resolvedBuildId)) {
            throw BuildNotFound("Build info not found for $resolvedBuildId")
        }

        val packageFilter = packageNamePattern?.takeIf { it.isNotBlank() }
        val classFilter = classNamePattern?.takeIf { it.isNotBlank() }

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
    ): List<ClassCoverageView> = transaction {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }
        metricsRepository.getClassCoverage(
            buildId = buildId,
            packageName = packageName?.takeIf { it.isNotBlank() },
            coverageTestTags = testTags,
            coverageAppEnvIds = envIds,
            coverageBranches = branches,
        ).map(::mapToClassCoverageView)
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
            metricsRepository.getImpactedTestsCount(targetBuildId, baselineBuildId)
        }
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
        pageSize: Int?
    ): PagedList<MethodView> = transaction {
        val targetBuildId = build.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Target build info not found for ${build.id}")

        val baselineBuildId = baselineBuild.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Baseline build info not found for ${baselineBuild.id}")

        // Map response field names to database column names
        val sortingFieldMapping = mapOf(
            "signature" to "signature",
            "className" to "class_name",
            "name" to "method_name",
            "impactedTests" to "impacted_tests"
        )
        val mappedSortBy = sortBy?.let { sortingFieldMapping[it] ?: it }

        return@transaction pagedListOf(
            page = page ?: 1,
            pageSize = pageSize ?: metricsConfig.pageSize
        ) { offset, limit ->
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
            ).map(::mapToMethodView)
        } withTotal {
            metricsRepository.getImpactedMethodsCount(targetBuildId, baselineBuildId)
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
        return ClassCoverageView(
            className = row["class_name"] as String,
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

    private fun mapToMethodView(resultSet: Map<String, Any?>): MethodView = MethodView(
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
        impactedTests = (resultSet["impacted_tests"] as Number?)?.toInt(),
    )

    // TODO good candidate to be moved to common functions (probably)
    private fun getUriString(baseUrl: String, path: String, queryParams: Map<String, String>): String {
        val uri = URI(baseUrl).resolve(path)
        val queryString = queryParams.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8.toString())}"
        }
        return URI("$uri?$queryString").toString()
    }
}
