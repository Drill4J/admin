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
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.util.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

private val logger = logger {}

/**
 * Test session
 */
@Serializable
sealed class Session : Sequence<ExecClassData> {
    /**
     * The session ID
     */
    abstract val id: String

    /**
     * The type of tests (for example: AUTO, MANUAL)
     */
    abstract val testType: String

    /**
     * All test received from autotest agent as [TestInfo] and for manual tests from [ExecClassData]
     */
    abstract val tests: Set<TestOverview>
}


private typealias ProbeKey = Pair<String, String>

class ActiveSession(
    override val id: String,
    override val testType: String,
    val isGlobal: Boolean = false,
    val isRealtime: Boolean = false,
    val testName: String? = null,
    private val labels: Set<Label> = emptySet(),
) : Session() {

    override val tests: Set<TestOverview>
        get() = _probes.value.run {
            _testTestInfo.value.takeIf { it.any() }?.let { tests ->
                val autotests = tests.values.map {
                    TestOverview(
                        testId = it.id.weakIntern(),
                        duration = it.finishedAt - it.startedAt,
                        details = it.details.copy(labels = it.details.labels + labels),
                        result = it.result
                    )
                }
                val manualTests = keys.filter { (id, _) -> id !in tests }.map { (id, name) ->
                    TestOverview(
                        testId = id,
                        details = TestDetails(testName = name.urlDecode().weakIntern(), labels = labels)
                    )
                }
                autotests + manualTests
            } ?: keys.map { (id, name) ->
                TestOverview(
                    testId = id,
                    details = TestDetails(testName = name.urlDecode().weakIntern(), labels = labels)
                )
            }
        }.toSet()

    private val _probes = atomic(
        persistentMapOf<ProbeKey, PersistentMap<Long, ExecClassData>>()
    )

    private val _testTestInfo = atomic<PersistentMap<String, TestInfo>>(persistentHashMapOf())

    private val _updatedTests = atomic<Set<String>>(setOf())

    val updatedTests: Set<TestKey>
        get() = _updatedTests.getAndUpdate { setOf() }.mapTo(mutableSetOf()) { it.testKey(testType) }

    /**
     * Add and merge new probes with current
     * @param dataPart a collection of new probes
     * @features Sending coverage data
     */
    fun addAll(dataPart: Collection<ExecClassData>) = dataPart.map { probe ->
        probe.id?.let { probe } ?: probe.copy(id = probe.id())
    }.forEach { probe ->
        if (true in probe.probes) {
            val test = probe.testId.weakIntern() to probe.testName.weakIntern()
            _probes.update { map ->
                (map[test] ?: persistentHashMapOf()).let { testData ->
                    val probeId = probe.id()
                    if (probeId in testData) {
                        testData.getValue(probeId).run {
                            val merged = probes.merge(probe.probes)
                            merged.takeIf { it != probes }?.let {
                                addUpdatedTest(probe.testId)
                                testData.put(probeId, copy(probes = merged))
                            }
                        }
                    } else testData.put(probeId, probe).also { addUpdatedTest(probe.testId) }
                }?.let { map.put(test, it) } ?: map
            }
        }
    }

    private fun addUpdatedTest(testId: String) {
        if (testId in _testTestInfo.value) _updatedTests.update { it + testId }
    }

    /**
     * Add new completed tests to the test session
     * @param testRun the list of new completed tests
     * @features Running tests
     */
    fun addTests(testRun: List<TestInfo>) {
        val testMap = testRun.associateBy { it.id }
        _testTestInfo.update { current -> current.putAll(testMap) }
        _updatedTests.update { it + testMap.keys }
    }

    override fun iterator(): Iterator<ExecClassData> = Sequence {
        _probes.value.values.asSequence().flatMap { it.values.asSequence() }.iterator()
    }.iterator()

    fun finish() = _probes.value.run {
        logger.debug { "ActiveSession finish with size = $size " }
        FinishedSession(
            id = id,
            testType = testType,
            tests = tests,
            probes = values.flatMap { it.values },
        )
    }
}

@Serializable
data class FinishedSession(
    override val id: String,
    override val testType: String,
    override val tests: Set<TestOverview>,
    val probes: List<ExecClassData>,
) : Session() {
    override fun iterator(): Iterator<ExecClassData> = probes.iterator()

    override fun equals(other: Any?): Boolean = other is FinishedSession && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

