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
package com.epam.drill.admin.metrics.service

import com.epam.drill.admin.metrics.models.BaselineBuild
import com.epam.drill.admin.metrics.models.Build
import com.epam.drill.admin.metrics.models.BuildSortField
import com.epam.drill.admin.metrics.models.CoverageCriteria
import com.epam.drill.admin.metrics.models.MethodCriteria
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.models.TestCriteria
import com.epam.drill.admin.metrics.views.*
import java.time.Instant

interface MetricsService {
    suspend fun getApplications(
        groupId: String? = null,
        freshAfter: Instant? = null,
    ): List<ApplicationView>

    suspend fun getBuilds(
        groupId: String,
        appId: String,
        branch: String?,
        envId: String?,
        commitSha: String?,
        buildVersion: String?,
        sortBy: BuildSortField? = null,
        sortOrder: SortOrder? = null,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant? = null,
    ): PagedList<BuildView>

    suspend fun getCoverageTreemap(
        buildId: String,
        testTag: String?,
        envId: String?,
        branch: String?,
        packageNamePattern: String?,
        classNamePattern: String?,
        rootId: String?,
        testSessionId: String? = null,
        testDefinitionId: String? = null,
        freshAfter: Instant? = null,
    ): List<Any>

    suspend fun getChangesCoverageTreemap(
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
        freshAfter: Instant? = null,
    ): List<Any>

    suspend fun getBuildDiffReport(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
        coverageThreshold: Double,
        freshAfter: Instant? = null,
    ): Map<String, Any?>

    suspend fun getChanges(
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
        freshAfter: Instant? = null,
    ): PagedList<MethodView>

    suspend fun getCoverage(
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
        freshAfter: Instant? = null,
    ): PagedList<MethodView>

    suspend fun getImpactedTests(
        build: Build,
        baselineBuild: BaselineBuild,
        testCriteria: TestCriteria = TestCriteria.NONE,
        methodCriteria: MethodCriteria = MethodCriteria.NONE,
        coverageCriteria: CoverageCriteria = CoverageCriteria.NONE,
        impactStatuses: List<TestImpactStatus> = listOf(TestImpactStatus.IMPACTED),
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant? = null,
    ): PagedList<TestView>

    suspend fun getImpactedMethods(
        build: Build,
        baselineBuild: BaselineBuild,
        testCriteria: TestCriteria = TestCriteria.NONE,
        methodCriteria: MethodCriteria = MethodCriteria.NONE,
        coverageCriteria: CoverageCriteria = CoverageCriteria.NONE,
        sortBy: String? = null,
        sortOrder: SortOrder? = null,
        page: Int?,
        pageSize: Int?,
        freshAfter: Instant? = null,
    ): PagedList<MethodView>
}