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
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.serialization.*

@Serializable
data class BuildTests(
    val tests: GroupedTests = emptyMap(),
)

internal fun BundleCounters.testsWith(
    methods: Iterable<Method>,
): GroupedTests = byTest.asSequence().takeIf { it.any() }?.run {
    val lazyMethodMap = lazy(LazyThreadSafetyMode.NONE) {
        methods.groupBy(Method::ownerClass)
    }
    val lazyPackageSet = lazy(LazyThreadSafetyMode.NONE) { methods.toPackageSet() }
    mapNotNull { (test, counter) ->
        test.takeIf {
            val packageSeq = counter.packages.asSequence()
            packageSeq.toCoveredMethods(lazyMethodMap::value, lazyPackageSet::value).any()
        }
    }.distinct().groupBy(TestKey::type) {
        TestData(
            id = it.id,
            details = byTestOverview[it]?.details ?: TestDetails.emptyDetails,
        )
    }
}.orEmpty()

internal fun GroupedTests.filter(
    predicate: (String, String) -> Boolean,
): GroupedTests = asSequence().mapNotNull { (type, testData) ->
    val filtered = testData.filter { predicate(it.id, type) }
    filtered.takeIf { it.any() }?.let { type to it }
}.toMap()

internal fun GroupedTests.withoutCoverage(
    bundleCounters: BundleCounters,
): GroupedTests = filter { id, type ->
    id.testKey(type) !in bundleCounters.byTest
}

internal fun GroupedTests.withCoverage(
    bundleCounters: BundleCounters,
): GroupedTests = filter { id, type ->
    id.testKey(type) in bundleCounters.byTest
}

internal fun CoverContext.testsToRunDto(
    bundleCounters: BundleCounters = build.bundleCounters,
): List<TestCoverageDto> = testsToRun.flatMap { (type, testData) ->
    testData.map { test ->
        val testKey = test.id.testKey(type)
        TestCoverageDto(
            id = testKey.id(),
            type = type,
            toRun = testKey !in bundleCounters.byTest,
            coverage = bundleCounters.byTest[testKey]?.toCoverDto(packageTree) ?: CoverDto.empty,
            overview = bundleCounters.byTestOverview[testKey] ?: TestOverview(
                testId = test.id,
                details = test.details
            )
        )
    }
}

internal fun List<TestCoverageDto>.toDto(agentId: String) = TestsSummaryDto(
    agentId = agentId,
    tests = this,
    totalCount = this.size
)

internal fun GroupedTests.totalDuration(
    detailsByTest: Map<TestKey, TestOverview>,
): Long = this.flatMap { (type, testData) ->
    testData.map { test ->
        val typedTest = TestKey(type = type, id = test.id)
        detailsByTest[typedTest]?.duration
    }
}.filterNotNull().sum()


internal fun GroupedTests.toDto() = GroupedTestsDto(
    totalCount = totalCount(),
    byType = this
)

internal fun GroupedTests.totalCount(): Int = values.sumOf { it.count() }
