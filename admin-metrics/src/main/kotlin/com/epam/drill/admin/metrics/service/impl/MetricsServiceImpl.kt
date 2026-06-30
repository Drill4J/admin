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
import com.epam.drill.admin.common.service.parseBuildId
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlOrchestrator
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
import java.time.Instant
import java.time.LocalDateTime

class MetricsServiceImpl(
    private val metricsRepository: MetricsRepository,
    private val metricsServiceUiLinksConfig: MetricsServiceUiLinksConfig,
    private val testRecommendationsConfig: TestRecommendationsConfig,
    private val metricsConfig: MetricsConfig,
    private val etl: EtlOrchestrator,
    private val testDefinitionCoverageEtl: EtlOrchestrator,
) : MetricsService {

    private val logger = KotlinLogging.logger {}

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
        branch: String?,
        envId: String?,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant?,
    ): PagedList<BuildView> {
        return pagedFreshListOf(groupId, page, pageSize, freshAfter) { offset, limit ->
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
        rootId: String?,
        testSessionId: String?,
        testDefinitionId: String?,
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
                testDefinitionCoverageEtl.run(
                    EtlContext(
                        groupId = parseBuildId(buildId).groupId,
                        testSessionId = testSessionId,
                        testDefinitionId = testDefinitionId
                    ),
                    finalTimestamp = freshAfter
                )
                metricsRepository.getMethodsWithCoverageByTestDefinition(
                    buildId = buildId,
                    testSessionId = resolvedTestSessionId,
                    testDefinitionId = testDefinitionId,
                    packageNamePattern = methodCriteria.packageNamePattern,
                    methodSignaturePattern = methodCriteria.signaturePattern,
                    coverageAppEnvIds = envId?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),
                )
            }

            testSessionId != null -> {
                metricsRepository.getMethodsWithCoverageByTestSession(
                    buildId = buildId,
                    testSessionId = testSessionId,
                    packageNamePattern = methodCriteria.packageNamePattern,
                    methodSignaturePattern = methodCriteria.signaturePattern,
                    coverageAppEnvIds = envId?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),
                    testTags = testTag?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),
                )
            }

            else -> {
                metricsRepository.getMethodsWithCoverage(
                    buildId = buildId,
                    coverageTestTag = testTag?.takeIf { it.isNotBlank() },
                    coverageEnvId = envId?.takeIf { it.isNotBlank() },
                    coverageBranch = branch?.takeIf { it.isNotBlank() },
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
        testTag: String?,
        envId: String?,
        branch: String?,
        packageNamePattern: String?,
        classNamePattern: String?,
        rootId: String?,
        includeDeleted: Boolean?,
        includeEqual: Boolean?,
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
        pageSize: Int?,
        freshAfter: Instant?,
    ): PagedList<MethodView> {
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

        return pagedFreshListOf(groupId, page, pageSize, freshAfter) { offset, limit ->
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
        pageSize: Int?,
        freshAfter: Instant?,
    ): PagedList<MethodView> {
        val buildId = generateBuildId(groupId, appId, instanceId, commitSha, buildVersion)
        if (!metricsRepository.buildExists(buildId)) {
            throw BuildNotFound("Build info not found for $buildId")
        }

        return pagedFreshListOf(groupId, page, pageSize, freshAfter) { offset, limit ->
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

        // Map response field names to database column names
        val sortingFieldMapping = mapOf(
            "testPath" to "test_path",
            "testName" to "test_name",
            "testRunner" to "test_runner",
            "impactedMethods" to "impacted_methods"
        )
        val mappedSortBy = sortBy?.let { sortingFieldMapping[it] ?: it }

        return pagedFreshListOf(build.groupId, page, pageSize, freshAfter) { offset, limit ->
            metricsRepository.getImpactedTests(
                targetBuildId = targetBuildId,
                baselineBuildId = baselineBuildId,

                testTags = testCriteria.testTags,
                testPathPattern = testCriteria.testPath,
                testNamePattern = testCriteria.testName,

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
                    tags = data["test_tags"] as List<String>?,
                    metadata = data["test_metadata"] as JsonElement?,
                    impactStatus = (data["impact_status"] as String).let { TestImpactStatus.valueOf(it) },
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

        // Map response field names to database column names
        val sortingFieldMapping = mapOf(
            "signature" to "signature",
            "className" to "class_name",
            "name" to "method_name",
            "impactedTests" to "impacted_tests"
        )
        val mappedSortBy = sortBy?.let { sortingFieldMapping[it] ?: it }

        return pagedFreshListOf(build.groupId, page, pageSize, freshAfter) { offset, limit ->
            metricsRepository.getImpactedMethods(
                targetBuildId = targetBuildId,
                baselineBuildId = baselineBuildId,

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
        }
    }

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

    private suspend fun refresh(groupId: String, freshAfter: Instant?): Instant? {
        return freshAfter?.let {
            etl.run(EtlContext(groupId), finalTimestamp = it).minOfOrNull { result ->
                result.lastProcessedAt
            }
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
}
