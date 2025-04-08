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

    suspend fun getBuilds(groupId: String, appId: String, branch: String?): List<Map<String, Any>>

    suspend fun getBuildDiffReport(
        buildId: String,
        baselineBuildId: String,
        coverageThreshold: Double
    ): Map<String, String>

    suspend fun refreshMaterializedView(viewName: String)

    suspend fun getRecommendedTests(
        targetBuildId: String,
        baselineBuildId: String? = null,
        testsToSkip: Boolean = false,
        testTaskId: String? = null,
        coveragePeriodFrom: LocalDateTime ?= null
    ): List<Map<String, Any?>>
}