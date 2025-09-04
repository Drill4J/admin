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

import java.time.LocalDateTime

interface MetricsRepository {

    suspend fun buildExists(buildId: String): Boolean

    suspend fun getApplications(groupId: String? = null): List<Map<String, Any?>>

    suspend fun getBuilds(
        groupId: String, appId: String,
        branch: String? = null, envId: String? = null,
        offset: Int? = null, limit: Int? = null
    ): List<Map<String, Any?>>

    suspend fun getBuildsCount(
        groupId: String, appId: String,
        branch: String? = null, envId: String? = null
    ): Long

    suspend fun getMethodsWithCoverage(
        buildId: String,
        coverageTestTag: String? = null,
        coverageEnvId: String? = null,
        coverageBranch: String? = null,
        packageName: String? = null,
        className: String? = null,
        offset: Int? = null, limit: Int? = null
    ): List<Map<String, Any?>>

    suspend fun getMethodsCount(
        buildId: String,
        packageNamePattern: String? = null,
        classNamePattern: String? = null,
    ): Long

    suspend fun getChangesWithCoverage(
        buildId: String,
        baselineBuildId: String? = null,
        coverageTestTag: String? = null,
        coverageEnvId: String? = null,
        coverageBranch: String? = null,
        packageName: String? = null,
        className: String? = null,
        offset: Int? = null, limit: Int? = null
    ): List<Map<String, Any?>>

    suspend fun getChangesCount(
        buildId: String,
        baselineBuildId: String? = null,
        packageNamePattern: String? = null,
        classNamePattern: String? = null,
    ): Long

    suspend fun getBuildDiffReport(
        buildId: String,
        baselineBuildId: String
    ): Map<String, String?>

    suspend fun getRecommendedTests(
        targetBuildId: String,
        baselineBuildId: String? = null,
        testsToSkip: Boolean = false,
        testTaskId: String? = null,
        coveragePeriodFrom: LocalDateTime? = null,
    ): List<Map<String, Any?>>

    suspend fun getImpactedTests(
        targetBuildId: String,
        baselineBuildId: String,
        testTaskId: String? = null,
        testTag: String?,
        testPathPattern: String?,
        testNamePattern: String?,
        offset: Int? = null, limit: Int? = null
    ): List<Map<String, Any?>>

    suspend fun getImpactedMethods(
        targetBuildId: String,
        baselineBuildId: String,
        testTaskId: String? = null,
        testTag: String? = null,
        testPathPattern: String? = null,
        testNamePattern: String? = null,
        offset: Int? = null, limit: Int? = null
    ): List<Map<String, Any?>>

    suspend fun refreshMaterializedView(viewName: String, concurrently: Boolean = true)
}