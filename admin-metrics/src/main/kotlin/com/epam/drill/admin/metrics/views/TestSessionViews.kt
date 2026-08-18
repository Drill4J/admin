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
package com.epam.drill.admin.metrics.views

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class TestSessionView(
    val testSessionId: String,
    val groupId: String,
    val appId: String? = null,
    val buildId: String? = null,
    val testTaskId: String?,
    val sessionStartedAt: LocalDateTime?,
    val createdBy: String?,
    val testDefinitions: Int,
    val testLaunches: Int,
    val result: String,
    val testDuration: Long,
    val testDurationFormatted: String,
    val failed: Int,
    val passed: Int,
    val skipped: Int,
    val smartSkipped: Int,
    val success: Int,
    val successRate: Double,
    val timeSaved: Long,
    val timeSavedFormatted: String,
)

@Serializable
data class TestSessionFilterOptionsView(
    val testTaskIds: List<String>,
    val createdBys: List<String>,
    val results: List<String>,
)

@Serializable
data class TestSessionBuildView(
    val appId: String,
    val buildId: String,
    val buildVersion: String?,
    val branch: String?,
    val coveredProbes: Int,
    val totalProbes: Int,
    val coveredMethods: Int,
    val totalMethods: Int,
)

@Serializable
data class TestSessionDetailView(
    val testSessionId: String,
    val groupId: String,
    val appId: String,
    val buildId: String,
    val buildVersion: String?,
    val branch: String?,
    val testTaskId: String?,
    val sessionStartedAt: LocalDateTime?,
    val createdBy: String?,
    val testDefinitions: Int,
    val testLaunches: Int,
    val result: String,
    val testDuration: Long,
    val testDurationFormatted: String,
    val failed: Int,
    val passed: Int,
    val skipped: Int,
    val smartSkipped: Int,
    val success: Int,
    val successRate: Double,
    val timeSaved: Long,
    val timeSavedFormatted: String,
)

@Serializable
data class TestDefinitionView(
    val testDefinitionId: String,
    val testName: String?,
    val testPath: String?,
    val testRunner: String?,
    val testResult: String,
    val testLaunches: Int,
)

@Serializable
data class TestSessionCoverageSummaryView(
    val probes: CoverageUnitSummaryView,
    val methods: CoverageUnitSummaryView,
)

@Serializable
data class TestLaunchView(
    val testDefinitionId: String,
    val testName: String?,
    val testPath: String?,
    val testRunner: String?,
    val testTags: List<String>,
    val testLaunches: Int,
    val testDuration: Long,
    val testDurationFormatted: String,
    val testResult: String,
)

@Serializable
data class TestFileLaunchView(
    val testPath: String,
    val testDefinitions: Int,
    val testLaunches: Int,
    val result: String,
    val failed: Int,
    val passed: Int,
    val skipped: Int,
    val smartSkipped: Int,
    val success: Int,
    val testDuration: Long,
    val testDurationFormatted: String,
    val successRate: Double,
)

@Serializable
data class TestFileLaunchFilterOptionsView(
    val testPaths: List<String>,
    val results: List<String>,
)

@Serializable
data class TestLaunchFilterOptionsView(
    val testNames: List<String>,
    val testTags: List<String>,
    val testResults: List<String>,
)

@Serializable
data class TablePageView(
    val page: Int,
)
