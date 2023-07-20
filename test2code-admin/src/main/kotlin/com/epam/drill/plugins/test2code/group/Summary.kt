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
package com.epam.drill.plugins.test2code.group

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

//TODO move agent summary out of the group package

internal data class AgentSummary(
    val name: String,
    val buildVersion: String,
    val coverage: Count,
    val coverageByType: Map<String, Count>,
    val methodCount: Count,
    val scopeCount: Int,
    val arrow: ArrowType,
    val riskCounts: RiskCounts = RiskCounts(),
    val risks: TypedRisks,
    val tests: GroupedTests,
    val testsCoverage: List<TestCoverageDto>,
    val testDuration: Long,
    val testsToRun: GroupedTests,
    val durationByType: GroupedDuration,
)
internal typealias GroupedDuration = Map<String, Long>

internal typealias AgentSummaries = PersistentMap<String, AgentSummary>

internal val summaryAggregator = SummaryAggregator()

internal class SummaryAggregator : (String, String, AgentSummary) -> AgentSummary {
    private val _summaryCache = atomic(
        persistentHashMapOf<String, AgentSummaries>()
    )

    override operator fun invoke(
        serviceGroup: String,
        agentId: String,
        agentSummary: AgentSummary,
    ): AgentSummary = run {
        val cache = _summaryCache.updateAndGet {
            val curAgentSummary = it[serviceGroup] ?: persistentHashMapOf()
            it.put(serviceGroup, curAgentSummary.put(agentId, agentSummary))
        }
        cache[serviceGroup]?.values?.reduce { acc, element ->
            acc + element
        } ?: agentSummary
    }

    fun getSummaries(serviceGroup: String): Map<String, AgentSummary> = _summaryCache.value[serviceGroup] ?: emptyMap()
}

internal fun CachedBuild.toSummary(
    agentName: String,
    testsToRun: GroupedTests,
    risks: TypedRisks,
    coverageByTests: CoverageByTests? = null,
    testsCoverage: List<TestCoverageDto>? = null,
    parentCoverageCount: Count? = null,
): AgentSummary = run {
    val uncoveredRisks = risks.notCovered()
    AgentSummary(
        name = agentName,
        buildVersion = agentKey.buildVersion,
        coverage = stats.coverage,
        methodCount = stats.methodCount,
        coverageByType = stats.coverageByType,
        scopeCount = stats.scopeCount,
        arrow = parentCoverageCount.arrowType(stats.coverage),
        riskCounts = uncoveredRisks.toCounts(),
        risks = uncoveredRisks,
        testDuration = coverageByTests?.all?.duration ?: 0L,
        durationByType = coverageByTests?.byType?.groupBy { it.type }
            ?.mapValues { (_, values) -> values.sumOf { it.summary.duration } }
            ?: emptyMap(),
        tests = tests.tests,
        testsCoverage = testsCoverage ?: emptyList(),
        testsToRun = testsToRun.withoutCoverage(bundleCounters)
    )
}

internal fun AgentSummary.toDto(agentId: String) = AgentSummaryDto(
    id = agentId,
    buildVersion = buildVersion,
    name = name,
    summary = toDto()
)

internal fun AgentSummary.toDto() = SummaryDto(
    coverage = coverage.percentage(),
    coverageCount = coverage,
    methodCount = methodCount,
    scopeCount = scopeCount,
    arrow = arrow,
    risks = riskCounts.total, //TODO remove after changes on frontend
    riskCounts = riskCounts,
    testDuration = testDuration,
    tests = toTestTypeSummary(),
    testsToRun = testsToRun.toTestCountDto(),
    recommendations = recommendations()
)

internal operator fun AgentSummary.plus(
    other: AgentSummary,
): AgentSummary = copy(
    coverage = coverage + other.coverage,
    methodCount = methodCount + other.methodCount,
    coverageByType = coverageByType.merge(other.coverageByType) { count1, count2 ->
        count1 + count2
    },
    scopeCount = scopeCount + other.scopeCount,
    arrow = ArrowType.UNCHANGED,
    risks = emptyMap(),
    riskCounts = riskCounts + other.riskCounts,
    testDuration = testDuration + other.testDuration,
    tests = tests.merge(other.tests, ::mergeDistinct),
    testsToRun = testsToRun.merge(other.testsToRun, ::mergeDistinct),
    durationByType = durationByType.merge(other.durationByType) { duration1, duration2 ->
        duration1 + duration2
    }
)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
operator fun Count.plus(other: Count): Count = copy(
    covered = covered + other.covered,
    total = total + other.total
)

private fun GroupedTests.toTestCountDto() = TestCountDto(
    count = totalCount(),
    byType = mapValues { it.value.count() }
)

private operator fun RiskCounts.plus(other: RiskCounts) = RiskCounts(
    new = new + other.new,
    modified = modified + other.modified,
    total = total + other.total
)

private fun AgentSummary.toTestTypeSummary() = coverageByType.map { (type, count) ->
    count.copy(total = coverage.total).let {
        TestTypeSummary(
            type = type,
            summary = TestSummary(
                coverage = CoverDto(
                    percentage = it.percentage(),
                    count = it.toDto()
                ),
                testCount = tests[type]?.count() ?: 0,
                duration = durationByType[type] ?: 0L
            )
        )
    }
}

private fun <K, V> Map<K, V>.merge(
    other: Map<K, V>,
    operation: (V, V) -> V,
): Map<K, V> = mapValuesTo(mutableMapOf<K, V>()) { (k, v) ->
    other[k]?.let { operation(v, it) } ?: v
}.also { map ->
    other.forEach { (k, v) ->
        if (k !in map) {
            map[k] = v
        }
    }
}

private fun <T> mergeDistinct(
    list1: List<T>,
    list2: List<T>,
): List<T> = sequenceOf(list1, list2).map { it.asSequence() }.flatten().distinct().toList()
