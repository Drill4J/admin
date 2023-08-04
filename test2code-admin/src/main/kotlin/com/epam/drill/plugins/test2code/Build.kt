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
package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.group.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.dsm.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

data class CachedBuild(
    val agentKey: AgentKey,
    val parentVersion: String = "",
    val probes: PersistentMap<Long, ExecClassData> = persistentHashMapOf(),
    val bundleCounters: BundleCounters = BundleCounters.empty,
    val stats: BuildStats = BuildStats(agentKey),
    val tests: BuildTests = BuildTests(),
)

@Serializable
data class BuildStats(
    @Id val agentKey: AgentKey,
    val coverage: Count = zeroCount,
    val methodCount: Count = zeroCount,
    val coverageByType: Map<String, Count> = emptyMap(),
    val scopeCount: Int = 0,
)

internal fun BuildCoverage.toCachedBuildStats(
    context: CoverContext,
): BuildStats = context.build.stats.copy(
    coverage = count,
    methodCount = methodCount,
    coverageByType = byTestType.associate {
        it.type to Count(
            it.summary.coverage.count.covered,
            it.summary.coverage.count.total
        )
    },
    scopeCount = finishedScopesCount
)

internal fun AgentSummary.recommendations(): Set<String> = sequenceOf(
    "Run recommended tests to cover modified methods".takeIf { testsToRun.any() },
    "Update your tests to cover risks".takeIf { riskCounts.total > 0 }
).filterNotNullTo(mutableSetOf())

internal fun CoverContext.toBuildStatsDto(): BuildStatsDto = BuildStatsDto(
    parentVersion = parentBuild?.agentKey?.buildVersion ?: "",
    total = methods.count(),
    new = methodChanges.new.count(),
    modified = methodChanges.modified.count(),
    unaffected = methodChanges.unaffected.count(),
    deleted = methodChanges.deleted.count()
)
