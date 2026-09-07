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
data class CoverageTrendPointView(
    val buildId: String,
    val buildLabel: String,
    val buildDate: LocalDateTime?,
    /** Isolated / own coverage percent (0–100). */
    val isolatedCoveragePercent: Double,
    /**
     * Additional coverage from other builds only (aggregated − isolated), 0–100.
     * Stacked on top of [isolatedCoveragePercent]; sum equals aggregated coverage.
     */
    val otherBuildsCoveragePercent: Double,
    /** Aggregated coverage percent (0–100); always >= [isolatedCoveragePercent]. */
    val aggregatedCoveragePercent: Double,
)

@Serializable
data class ChangesTrendPointView(
    val buildId: String,
    val buildLabel: String,
    val buildDate: LocalDateTime?,
    /** Probe (code) change totals vs baseline. */
    val totalProbes: Int,
    val coveredProbes: Int,
    /**
     * Aggregated covered probes (own + other builds); always >= [coveredProbes].
     * Matches Metabase "Covered in other builds" (absolute aggregated, not delta).
     */
    val coveredInOtherBuildsProbes: Int,
    /** Method change totals vs baseline. */
    val totalMethods: Int,
    val coveredMethods: Int,
    /**
     * Aggregated covered methods (own + other builds); always >= [coveredMethods].
     */
    val coveredInOtherBuildsMethods: Int,
)
