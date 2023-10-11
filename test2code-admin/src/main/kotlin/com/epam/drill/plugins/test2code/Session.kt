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
import com.epam.dsm.util.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*


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


typealias ProbeKey = String

class TestSession(
    override val id: String,
    override val testType: String,
    val isGlobal: Boolean = false,
    val isRealtime: Boolean = false,
    val testName: String? = null,
    private val labels: Set<Label> = emptySet(),
) : Session() {

    override val tests: Set<TestOverview>
        get() = _testTestInfo.value.values.map { testInfo ->
            TestOverview(
                testId = testInfo.id,
                duration = getDuration(testInfo.startedAt, testInfo.finishedAt),
                details = testInfo.details,
                result = testInfo.result
            )
        }.toSet()

    private fun getDuration(start: Long?, end: Long?): Long {
        if (start == null || end == null) return 0
        return end - start
    }

    private val _probes = atomic(persistentMapOf<ProbeKey, PersistentMap<Long, ExecClassData>>())

    val probes get() = _probes.value

    private val _testTestInfo = atomic(persistentMapOf<String, TestInfo>())

    private val _updatedTests = atomic(setOf<String>())

    val updatedTests: Set<TestKey>
        get() = _updatedTests.getAndUpdate { setOf() }.mapTo(mutableSetOf()) { it.testKey(testType) }

    /**
     * Add and merge new probes with current
     * @param dataPart a collection of new probes
     * @features Sending coverage data
     */
    fun addAll(dataPart: Collection<ExecClassData>) = dataPart.map { probe ->
        probe.id?.let { probe } ?: probe.copy(id = probe.id())
    }.forEach { execData ->
        if (true in execData.probes) {
            val testKey = execData.testId.weakIntern()
            _probes.update { map ->
                (map[testKey] ?: persistentHashMapOf()).let { testData ->
                    val probeId = execData.id()
                    if (probeId in testData) {
                        testData.getValue(probeId).run {
                            val merged = probes.merge(execData.probes)
                            merged.takeIf { it != probes }?.let {
                                addUpdatedTest(execData.testId)
                                testData.put(probeId, copy(probes = merged))
                            }
                        }
                    } else testData.put(probeId, execData).also { addUpdatedTest(execData.testId) }
                }?.let { map.put(testKey, it) } ?: map
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

}
