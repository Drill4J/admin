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

import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.test.js.*
import com.epam.drill.plugins.test2code.util.*
import kotlinx.coroutines.*
import kotlin.test.*

class JsCoverageTest : PostgresBased("js_coverage") {

    private val autoTestType = "AUTO"

    private val manualTestType = "MANUAL"

    @Test
    fun `coverageData for active scope with custom js probes`() {
        runBlocking {
            val coverageData = calculateCoverage() {
                this.execSession(manualTestType) { sessionId ->
                    addProbes(sessionId) { probes }
                }
                this.execSession(autoTestType) { sessionId ->
                    addProbes(sessionId) { IncorrectProbes.underCount }
                    addProbes(sessionId) { IncorrectProbes.overCount }
                    addProbes(sessionId) { IncorrectProbes.notExisting }
                }
            }
            coverageData.run {
                assertEquals(Count(3, 5).toDto(), coverage.count.toDto())
                assertEquals(listOf("foo/bar"), packageCoverage.map { it.name })
                assertEquals(1, packageCoverage[0].coveredClassesCount)
                assertEquals(1, packageCoverage[0].totalClassesCount)
                assertEquals(2, packageCoverage[0].coveredMethodsCount)
                assertEquals(3, packageCoverage[0].totalMethodsCount)
                val expectedProbes = probes[0].probes
                packageCoverage[0].classes.run {
                    assertEquals(listOf("foo/bar"), map { it.path })
                    assertEquals(listOf("baz.js"), map { it.name })
                    assertEquals(listOf(100.0, 0.0, 50.0), flatMap { it.methods }.map { it.coverage })
                    this[0].run {
                        assertEquals(expectedProbes.toList(), probes)
                        assertEquals(3, methods.size)
                        methods[0].run {
                            assertEquals(ProbeRange(0, 1), probeRange)
                            assertEquals(2, probesCount)
                        }
                        methods[1].run {
                            assertEquals(ProbeRange(2, 2), probeRange)
                            assertEquals(1, probesCount)
                        }
                        methods[2].run {
                            assertEquals(ProbeRange(3, 4), probeRange)
                            assertEquals(2, probesCount)
                        }
                    }
                }
                assertEquals(
                    setOf(TypedTest(details = TestDetails(testName = "default"), type = manualTestType),
                        TypedTest(details = TestDetails(testName = "default"), type = autoTestType)),
                    associatedTests.getAssociatedTests().flatMap { it.tests }.toSet()
                )
                buildMethods.run {
                    assertEquals(2, totalMethods.coveredCount)
                    assertEquals(3, totalMethods.totalCount)
                }

            }
        }
    }

    @Test
    fun `should merge probes`() = runBlocking {
        val coverageData = calculateCoverage() {
            this.execSession(manualTestType) { sessionId ->
                addProbes(sessionId) { probes }
                addProbes(sessionId) { probes2 }
            }
        }
        coverageData.run {
            assertEquals(Count(4, 5), coverage.count)
            packageCoverage[0].classes.run {
                this[0].run {
                    assertEquals(5, probes.size)
                    assertEquals(listOf(true, true, true, true, false), probes)
                }
            }
        }
    }

    private suspend fun calculateCoverage(addProbes: suspend ActiveScope.() -> Unit): CoverageInfoSet {
        val adminData = object : AdminData {
        }
        val state = AgentState(
            storeClient, jsAgentInfo, adminData
        )
        state.init()
        (state.data as DataBuilder) += ast
        state.initialized()
        val active = state.activeScope
        active.addProbes()
        val finished = active.finish(enabled = true)
        val context = state.coverContext()
        val bundleCounters = finished.calcBundleCounters(context, emptyMap())
        return bundleCounters.calculateCoverageData(context)
    }

    private suspend fun ActiveScope.execSession(testType: String, block: suspend ActiveScope.(String) -> Unit) {
        val sessionId = genUuid()
        startSession(sessionId = sessionId, testType = testType)
        block(sessionId)
        finishSession(sessionId)
    }
}
