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

import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig.transaction
import com.epam.drill.admin.metrics.config.MetricsServiceUiLinksConfig
import com.epam.drill.admin.metrics.config.TestRecommendationsConfig
import com.epam.drill.admin.common.exception.BuildNotFound
import com.epam.drill.admin.metrics.repository.MetricsRepository
import com.epam.drill.admin.metrics.views.RecommendedTestsView
import com.epam.drill.admin.metrics.service.MetricsService
import com.epam.drill.admin.common.service.generateBuildId
import com.epam.drill.admin.metrics.config.MetricsConfig
import com.epam.drill.admin.metrics.views.ApplicationView
import com.epam.drill.admin.metrics.views.BuildView
import com.epam.drill.admin.metrics.views.ChangeType
import com.epam.drill.admin.metrics.views.MethodView
import com.epam.drill.admin.metrics.views.PagedList
import com.epam.drill.admin.metrics.views.TestView
import com.epam.drill.admin.metrics.views.pagedListOf
import com.epam.drill.admin.metrics.views.withTotal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.datetime.toKotlinLocalDateTime
import mu.KotlinLogging
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class MetricsServiceImpl(
    private val metricsRepository: MetricsRepository,
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
                    envIds = (it["env_ids"] as List<String>?) ?: emptyList(),
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
        testsToSkip: Boolean,
        testTaskId: String?,
        coveragePeriodDays: Int?,
        targetInstanceId: String?,
        targetCommitSha: String?,
        targetBuildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
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

        val recommendedTests = metricsRepository.getRecommendedTests(
            targetBuildId = targetBuildId,
            baselineBuildId = baselineBuildId,
            testsToSkip = testsToSkip,
            testTaskId = testTaskId,
            coveragePeriodFrom = coveragePeriodFrom,
        ).map { data ->
            RecommendedTestsView(
                testDefinitionId = data["test_definition_id"] as String,
                testRunner = data["runner"] as String,
                testPath = data["path"] as String,
                testName = data["name"] as String,
                tags = data["tags"]?.let { it as List<String> } ?: emptyList(),
                metadata = data["metadata"]?.let { it as Map<String, String> } ?: emptyMap()
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
            metricsRepository.getMethodsWithCoverage(
                buildId = buildId,
                baselineBuildId = baselineBuildId,
                offset = offset,
                limit = limit
            ).map(::mapToMethodView)
        } withTotal {
            metricsRepository.getMethodsCount(buildId = buildId, baselineBuildId = baselineBuildId)
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
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
        testTag: String?,
        testTaskId: String?,
        testPathPattern: String?,
        page: Int?,
        pageSize: Int?
    ): PagedList<TestView> = transaction {
        val baselineBuildId = generateBuildId(
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

        val targetBuildId = generateBuildId(
            groupId,
            appId,
            instanceId,
            commitSha,
            buildVersion,
            """
            Provide at least one the following: instanceId, commitSha, buildVersion
            """.trimIndent()
        )
        if (!metricsRepository.buildExists(targetBuildId)) {
            throw BuildNotFound("Target build info not found for $targetBuildId")
        }

        return@transaction pagedListOf(
            page = page ?: 1,
            pageSize = pageSize ?: metricsConfig.pageSize
        ) { offset, limit ->
            metricsRepository.getImpactedTests(
                targetBuildId = targetBuildId,
                baselineBuildId = baselineBuildId,
                testTaskId = testTaskId,
                testTag = testTag,
                testPathPattern = testPathPattern,
                offset = offset,
                limit = limit,
            ).map { data ->
                TestView(
                    testDefinitionId = data["test_definition_id"] as String,
                    testRunner = data["runner"] as String,
                    testPath = data["path"] as String,
                    testName = data["name"] as String,
                    tags = data["tags"]?.let { it as List<String> } ?: emptyList(),
                    metadata = data["metadata"]?.let { it as Map<String, String> } ?: emptyMap(),
                    impactedMethods = data["impacted_methods"]?.let { it as List<Map<String, Any?>> }
                        ?.map(::mapToMethodView)
                        ?: emptyList()
                )
            }
        }
    }

    override suspend fun refreshMaterializedViews() = coroutineScope {
        logger.debug { "Refreshing materialized views..." }

        val methodsViewJob = async { refreshMaterializedView(methodsView) }
        val buildsViewJob = async { waitFor(methodsViewJob).run { refreshMaterializedView(buildsView) } }
        val methodsCoverageViewJob = async { waitFor(methodsViewJob).run { refreshMaterializedView(methodsCoverageView) } }
        val buildsComparisonViewJob = async { waitFor(methodsViewJob, buildsViewJob).run { refreshMaterializedView(buildsComparisonView) } }
        val testedBuildsComparisonViewJob = async { waitFor(methodsViewJob).run { refreshMaterializedView(testedBuildsComparisonView) } }
        val buildsCoverageViewJob = async { waitFor(methodsCoverageViewJob).run { refreshMaterializedView(buildsCoverageView) } }
        val testSessionBuildsCoverageViewJob = async { waitFor(methodsViewJob).run { refreshMaterializedView(testSessionBuildsCoverageView) } }
        waitFor(buildsComparisonViewJob, testedBuildsComparisonViewJob, buildsCoverageViewJob, testSessionBuildsCoverageViewJob)

        logger.debug { "Materialized views were refreshed." }
    }

    private fun mapToMethodView(resultSet: Map<String, Any?>): MethodView = MethodView(
        className = resultSet["classname"] as String,
        name = resultSet["name"] as String,
        params = (resultSet["params"] as String).split(",").map(String::trim),
        returnType = resultSet["return_type"] as String,
        changeType = ChangeType.fromString(resultSet["change_type"] as String?),
        probesCount = (resultSet["probes_count"] as Number?)?.toInt() ?: 0,
        coveredProbes = (resultSet["isolated_covered_probes"] as Number?)?.toInt() ?: 0,
        coveredProbesInOtherBuilds = (resultSet["aggregated_covered_probes"] as Number?)?.toInt() ?: 0,
        coverageRatio = (resultSet["isolated_probes_coverage_ratio"] as Number?)?.toDouble() ?: 0.0,
        coverageRatioInOtherBuilds = (resultSet["aggregated_probes_coverage_ratio"] as Number?)?.toDouble() ?: 0.0,
    )

    // TODO good candidate to be moved to common functions (probably)
    private fun getUriString(baseUrl: String, path: String, queryParams: Map<String, String>): String {
        val uri = URI(baseUrl).resolve(path)
        val queryString = queryParams.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8.toString())}"
        }
        return URI("$uri?$queryString").toString()
    }

    private suspend fun refreshMaterializedView(viewName: String) {
        val result = measureTimedValue {
            metricsRepository.refreshMaterializedView(viewName)
        }
        logger.debug("Materialized view $viewName was refreshed in ${formatDuration(result.duration)}.")
    }

    private fun formatDuration(duration: kotlin.time.Duration): String = when {
        duration.inWholeMinutes > 0 -> "${duration.inWholeMinutes} min"
        duration.inWholeSeconds > 0 -> "${duration.inWholeSeconds} sec"
        else -> "${duration.inWholeMilliseconds} ms"
    }

    private suspend fun waitFor(vararg dependents: Deferred<Unit>) {
        dependents.forEach { it.await() }
    }
}
