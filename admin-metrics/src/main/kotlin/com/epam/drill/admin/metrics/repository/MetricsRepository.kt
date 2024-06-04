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

import kotlinx.serialization.json.JsonObject

interface MetricsRepository {

    suspend fun getBuildDiffReport(
        groupId: String,
        appId: String,

        instanceId: String?,
        buildVersion: String?,
        commitSha: String?,

        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,

        coverageThreshold: Double
    ): Map<String, Any>

    suspend fun getRisksByBranchDiff(
        groupId: String,
        appId: String,
        currentBranch: String,
        currentVcsRef: String,
        baseBranch: String,
        baseVcsRef: String
    ): List<JsonObject>

    suspend fun getTotalCoverage(
        groupId: String,
        appId: String,
        currentVcsRef: String
    ): JsonObject

    suspend fun getSummaryByBranchDiff(
        groupId: String,
        appId: String,
        currentBranch: String,
        currentVcsRef: String,
        baseBranch: String,
        baseVcsRef: String
    ): JsonObject
}