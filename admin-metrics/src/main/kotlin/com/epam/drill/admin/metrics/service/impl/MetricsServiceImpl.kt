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
import com.epam.drill.admin.metrics.exception.BuildNotFound
import com.epam.drill.admin.metrics.repository.MetricsRepository
import com.epam.drill.admin.metrics.route.response.RecommendedTestsView
import com.epam.drill.admin.metrics.service.MetricsService
import com.epam.drill.admin.common.service.generateBuildId
import com.epam.drill.admin.common.service.getAppAndGroupIdFromBuildId
import com.epam.drill.admin.metrics.views.ApplicationView
import com.epam.drill.admin.metrics.views.BuildView
import kotlinx.datetime.toKotlinLocalDateTime
import mu.KotlinLogging
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime


class MetricsServiceImpl(
    private val metricsRepository: MetricsRepository,
    private val metricsServiceUiLinksConfig: MetricsServiceUiLinksConfig,
    private val testRecommendationsConfig: TestRecommendationsConfig
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
        size: Int?
    ): List<BuildView> {
        return transaction {
            metricsRepository.getBuilds(groupId, appId, branch, envId).map {
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

        val (groupId, appId) = getAppAndGroupIdFromBuildId(buildId)
        val data = metricsRepository.getMaterializedMethodsCoverage(
            groupId,
            appId,
            buildId,
            testTag,
            envId,
            branch,
            packageNamePattern,
            classNamePattern,
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
        useMaterializedViews: Boolean?
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
                coverageThreshold,
                useMaterializedViews ?: false
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
        useMaterializedViews: Boolean?
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
            useMaterializedViews = useMaterializedViews ?: false
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

    // TODO good candidate to be moved to common functions (probably)
    private fun getUriString(baseUrl: String, path: String, queryParams: Map<String, String>): String {
        val uri = URI(baseUrl).resolve(path)
        val queryString = queryParams.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8.toString())}"
        }
        return URI("$uri?$queryString").toString()
    }
}
