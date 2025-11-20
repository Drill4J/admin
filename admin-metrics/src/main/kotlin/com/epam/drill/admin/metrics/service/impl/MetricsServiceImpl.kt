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
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.metrics.config.MetricsConfig
import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig.transaction
import com.epam.drill.admin.metrics.config.MetricsServiceUiLinksConfig
import com.epam.drill.admin.metrics.config.TestRecommendationsConfig
import com.epam.drill.admin.metrics.models.BaselineBuild
import com.epam.drill.admin.metrics.models.Build
import com.epam.drill.admin.metrics.models.CoverageCriteria
import com.epam.drill.admin.metrics.models.MethodCriteria
import com.epam.drill.admin.metrics.models.TestCriteria
import com.epam.drill.admin.metrics.repository.MetricsRepository
import com.epam.drill.admin.metrics.service.MetricsService
import com.epam.drill.admin.metrics.views.*
import kotlinx.datetime.toKotlinLocalDateTime
import mu.KotlinLogging
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

class MetricsServiceImpl(
    private val metricsRepository: MetricsRepository,
    private val etl: EtlOrchestrator,
    private val metricsServiceUiLinksConfig: MetricsServiceUiLinksConfig,
    private val testRecommendationsConfig: TestRecommendationsConfig,
    private val metricsConfig: MetricsConfig,
) : MetricsService {

    private val logger = KotlinLogging.logger {}

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
        branch: String?,
        envId: String?,
        page: Int?,
        pageSize: Int?
    ): PagedList<BuildView> = transaction {
        pagedListOf(page = page ?: 1, pageSize = pageSize ?: metricsConfig.pageSize) { offset, limit ->
            metricsRepository.getBuilds(
                groupId, appId,
                branch, envId,
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
                branch, envId
            )
        }
    }


    override suspend fun getCoverageTreemap(
        buildId: String,
        testTag: String?,
        envId: String?,
        branch: String?,
        packageNamePattern: String?,
        classNamePattern: String?,
        rootId: String?
    ): List<Any> {
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }

        val data = metricsRepository.getMethodsWithCoverage(
            buildId = buildId,
            coverageTestTag = testTag?.takeIf { it.isNotBlank() },
            coverageEnvId = envId?.takeIf { it.isNotBlank() },
            coverageBranch = branch?.takeIf { it.isNotBlank() },
            packageName = packageNamePattern?.takeIf { it.isNotBlank() },
            className = classNamePattern?.takeIf { it.isNotBlank() }
        )

        return buildTree(data, rootId)
    }

    override suspend fun getChangesCoverageTreemap(
        buildId: String,
        baselineBuildId: String,
        testTag: String?,
        envId: String?,
        branch: String?,
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
            coverageTestTag = testTag?.takeIf { it.isNotBlank() },
            coverageEnvId = envId?.takeIf { it.isNotBlank() },
            coverageBranch = branch?.takeIf { it.isNotBlank() },
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
                baselineBuildId
            )

            val baseUrl = metricsServiceUiLinksConfig.baseUrl
            val buildTestingReportPath = metricsServiceUiLinksConfig.buildTestingReportPath
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
                        "changes" to null,
                        "recommended_tests" to null,
                        "build" to buildTestingReportPath?.run {
                            getUriString(
                                baseUrl = baseUrl,
                                path = buildTestingReportPath,
                                queryParams = mapOf(
                                    "build" to buildId,
                                )
                            )
                        },
                        "baseline_build" to buildTestingReportPath?.run {
                            getUriString(
                                baseUrl = baseUrl,
                                path = buildTestingReportPath,
                                queryParams = mapOf(
                                    "build" to baselineBuildId,
                                )
                            )
                        },
                        "full_report" to buildTestingReportPath?.run {
                            getUriString(
                                baseUrl = baseUrl,
                                path = buildTestingReportPath,
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
                metadata = data["test_metadata"] as Map<String, String>?,
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
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        testTag: String?,
        envId: String?,
        branch: String?,
        packageNamePattern: String?,
        classNamePattern: String?,
        page: Int?,
        pageSize: Int?
    ): PagedList<MethodView> = transaction {
        val buildId = generateBuildId(groupId, appId, instanceId, commitSha, buildVersion)
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }

        return@transaction pagedListOf(
            page = page ?: 1,
            pageSize = pageSize ?: metricsConfig.pageSize
        ) { offset, limit ->
            metricsRepository.getMethodsWithCoverage(
                buildId = buildId,
                coverageTestTag = testTag,
                coverageEnvId = envId,
                coverageBranch = branch,
                packageName = packageNamePattern,
                className = classNamePattern,
                offset = offset,
                limit = limit
            ).map(::mapToMethodView)
        } withTotal {
            metricsRepository.getMethodsCount(buildId = buildId)
        }
    }

    override suspend fun getImpactedTests(
        build: Build,
        baselineBuild: BaselineBuild,
        testCriteria: TestCriteria,
        methodCriteria: MethodCriteria,
        coverageCriteria: CoverageCriteria,
        page: Int?,
        pageSize: Int?
    ): PagedList<TestView> = transaction {
        val baselineBuildId = build.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Target build info not found for ${build.id}")

        val targetBuildId = baselineBuild.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Baseline build info not found for ${baselineBuild.id}")

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

                coverageBranches = coverageCriteria.branches,
                coverageAppEnvIds = coverageCriteria.appEnvIds,

                offset = offset,
                limit = limit,
            ).map { data ->
                TestView(
                    testDefinitionId = data["test_definition_id"] as String,
                    testPath = data["test_path"] as String,
                    testName = data["test_name"] as String,
                    testRunner = data["test_runner"] as String?,
                    tags = data["test_tags"] as List<String>?,
                    metadata = data["test_metadata"] as Map<String, String>?,
                    impactedMethods = (data["impacted_methods"] as Number?)?.toInt(),
                )
            }
        }
    }

    override suspend fun getImpactedMethods(
        build: Build,
        baselineBuild: BaselineBuild,
        testCriteria: TestCriteria,
        methodCriteria: MethodCriteria,
        coverageCriteria: CoverageCriteria,
        page: Int?,
        pageSize: Int?
    ): PagedList<MethodView> = transaction {
        val baselineBuildId = build.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Target build info not found for ${build.id}")

        val targetBuildId = baselineBuild.id.takeIf { metricsRepository.buildExists(it) }
            ?: throw BuildNotFound("Baseline build info not found for ${baselineBuild.id}")

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

                coverageBranches = coverageCriteria.branches,
                coverageAppEnvIds = coverageCriteria.appEnvIds,

                offset = null,
                limit = null
            ).map(::mapToMethodView)
        }
    }

    override suspend fun refresh(reset: Boolean) {
        val initTimestamp = metricsRepository.getMetricsPeriodDays()
        val results = if (reset)
            etl.rerun(initTimestamp, withDataDeletion = true)
        else
            etl.run(initTimestamp)
        if (results.any { !it.success }) {
            val errorMessages = results.mapNotNull { it.errorMessage }.joinToString(separator = "\n")
            throw IllegalStateException("Error(s) occurred during ETL process:\n$errorMessages")
        }
    }

    private fun mapToMethodView(resultSet: Map<String, Any?>): MethodView = MethodView(
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
