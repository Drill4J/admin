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
import com.epam.drill.admin.metrics.views.BuildView
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

    override suspend fun getBuilds(groupId: String, appId: String, branch: String?): List<BuildView> {
        return transaction {
            metricsRepository.getBuilds(groupId, appId, branch).map {
                BuildView(
                    id = it["id"] as String,
                    groupId = it["group_id"] as String,
                    appId = it["app_id"] as String,
                    commitSha = it["commit_sha"] as String?,
                    buildVersion = it["build_version"] as String?,
                    branch = it["branch"] as String?,
                    instanceId = it["instance_id"] as String?,
                    commitDate = it["commit_date"] as String?,
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
        classNamePattern: String?
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

        return buildTree(data)
    }

    private fun buildTree(data: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val nodeMap = mutableMapOf<String, MutableMap<String, Any?>>()
        val rootNodes = mutableSetOf<String>()

        // Step 1: Build full uncollapsed tree
        for (item in data) {
            val pathParts = (item["name"] as String).split("/")
            var currentPath = ""

            for ((index, part) in pathParts.withIndex()) {
                var nodePart = part
                if (index == pathParts.lastIndex) {
                    nodePart += "(${item["params"]}) -> ${item["return_type"]}"
                }

                currentPath = if (currentPath.isEmpty()) nodePart else "$currentPath/$nodePart"

                if (!nodeMap.containsKey(currentPath)) {
                    nodeMap[currentPath] = mutableMapOf(
                        "name" to nodePart,
                        "full_name" to currentPath,
                        "probes_count" to 0L,
                        "covered_probes" to 0L,
                        "children" to mutableSetOf<String>(),
                        "parent" to if (index == 0) null else pathParts.subList(0, index).joinToString("/"),
                        "params" to if (index == pathParts.lastIndex) item["params"] else null,
                        "return_type" to if (index == pathParts.lastIndex) item["return_type"] else null
                    )
                }

                if (index > 0) {
                    val parentPath = pathParts.subList(0, index).joinToString("/")
                    val parentNode = nodeMap.getValue(parentPath)
                    (parentNode["children"] as MutableSet<String>).add(currentPath)
                }
            }

            val leafNode = nodeMap.getValue(currentPath)
            leafNode["probes_count"] = item["probes_count"] as Long
            leafNode["covered_probes"] = item["covered_probes"] as Long
        }

        // Step 2: Collapse into a new map
        val collapsedNodeMap = mutableMapOf<String, MutableMap<String, Any?>>()

        fun collapseAndCopy(path: String, parentPath: String?): String {
            var node = nodeMap.getValue(path)
            var name = node["name"] as String
            var fullName = path
            var currentPath = path
            var children = node["children"] as Set<String>

            while (children.size == 1) {
                val childPath = children.first()
                val child = nodeMap[childPath] ?: break
                val grandChildren = child["children"] as Set<String>

                val isSecondToLast = grandChildren.any { (nodeMap[it]?.get("children") as? Set<*>)?.isEmpty() == true }
                if (isSecondToLast) break

                val childName = child["name"] as String
                name = "$name/$childName"
                fullName = child["full_name"] as String
                currentPath = fullName
                node = child
                children = grandChildren
            }

            val newNode = mutableMapOf(
                "name" to name,
                "full_name" to fullName,
                "parent" to parentPath,
                "probes_count" to node["probes_count"] as Long,
                "covered_probes" to node["covered_probes"] as Long,
                "params" to node["params"],
                "return_type" to node["return_type"],
                "children" to mutableSetOf<String>()
            )
            collapsedNodeMap[fullName] = newNode

            for (child in children) {
                val newChildPath = collapseAndCopy(child, fullName)
                (newNode["children"] as MutableSet<String>).add(newChildPath)
            }

            return fullName
        }

        for ((path, node) in nodeMap) {
            if (node["parent"] == null) rootNodes.add(path)
        }

        val newRoots = mutableSetOf<String>()
        for (root in rootNodes) {
            val collapsedRoot = collapseAndCopy(root, null)
            newRoots.add(collapsedRoot)
        }

        // Step 3: Validate
        fun validateTreeStructure() {
            for ((path, node) in collapsedNodeMap) {
                val children = node["children"] as Set<String>
                for (child in children) {
                    require(collapsedNodeMap.containsKey(child)) {
                        "Invalid tree: '$path' has non-existent child '$child'"
                    }
                    val childNode = collapsedNodeMap.getValue(child)
                    require(childNode["parent"] == path) {
                        "Invalid tree: child's parent mismatch at '$child'"
                    }
                }
            }

            for (item in data) {
                val pathParts = (item["name"] as String).split("/")
                var currentPath = ""
                for ((index, part) in pathParts.withIndex()) {
                    var nodePart = part
                    if (index == pathParts.lastIndex) {
                        nodePart += "(${item["params"]}) -> ${item["return_type"]}"
                    }
                    currentPath = if (currentPath.isEmpty()) nodePart else "$currentPath/$nodePart"
                }
                require(collapsedNodeMap.containsKey(currentPath)) {
                    "Missing node in collapsed tree: $currentPath"
                }
            }
        }

        validateTreeStructure()

        // Step 4: Aggregation
        fun computeAggregates(path: String) {
            val node = collapsedNodeMap.getValue(path)
            val children = node["children"] as MutableSet<String>
            for (child in children) {
                computeAggregates(child)
                val childNode = collapsedNodeMap.getValue(child)
                node["probes_count"] = (node["probes_count"] as Long) + (childNode["probes_count"] as Long)
                node["covered_probes"] = (node["covered_probes"] as Long) + (childNode["covered_probes"] as Long)
            }
        }

        for (root in newRoots) {
            computeAggregates(root)
        }

        // Step 5: Flatten
        return collapsedNodeMap.values.map {
            mapOf(
                "name" to it["name"],
                "full_name" to it["full_name"],
                "parent" to it["parent"],
                "probes_count" to it["probes_count"],
                "covered_probes" to it["covered_probes"],
                "params" to it["params"],
                "return_type" to it["return_type"]
            )
        }
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
        coverageThreshold: Double
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

            val metrics = metricsRepository.getBuildDiffReport(groupId, appId, buildId, baselineBuildId, coverageThreshold)

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
        baselineBuildVersion: String?
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

        val coveragePeriodFrom = (coveragePeriodDays ?: testRecommendationsConfig.coveragePeriodDays).let {
            LocalDateTime.now().minusDays(it.toLong())
        }

        val recommendedTests = metricsRepository.getRecommendedTests(
            targetBuildId = targetBuildId,
            baselineBuildId = baselineBuildId,
            testsToSkip = testsToSkip,
            testTaskId = testTaskId,
            coveragePeriodFrom = coveragePeriodFrom
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
