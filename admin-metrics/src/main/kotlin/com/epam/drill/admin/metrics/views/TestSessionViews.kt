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
    val appId: String,
    val buildId: String,
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
