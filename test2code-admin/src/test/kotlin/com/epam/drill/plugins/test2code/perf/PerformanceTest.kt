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
package com.epam.drill.plugins.test2code.perf

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.coroutines.*
import kotlin.random.*
import kotlin.test.*
import kotlin.test.Test

class PerformanceTest : PluginTest() {

    @Test
    fun `should start & finish session and collect coverage`() = runBlocking {
        val plugin: Plugin = initPlugin("0.1.0")
        val finishedSession = finishedSession(plugin, "sessionId", 1, 3)
        plugin.state.close()
        assertEquals(3, finishedSession?.probes?.size)
    }

    /**
     * when countAddProbes = 100 OutOfMemoryError @link [com.epam.drill.plugins.test2code.storage.storeSession]
     */
    @Test
    fun `perf test! should start & finish session and collect coverage`() = runBlocking {
        val plugin: Plugin = initPlugin("0.1.0")
        val finishedSession = finishedSession(plugin, "sessionId", 30)
        plugin.state.close()
        assertEquals(3000, finishedSession?.probes?.size)
    }


    private suspend fun finishedSession(
        plugin: Plugin,
        sessionId: String,
        countAddProbes: Int = 1,
        sizeExec: Int = 100,
        sizeProbes: Int = 10_000,
    ): TestSession? {
        plugin.sessionHolder.createSession(
            sessionId = sessionId,
            testType = "MANUAL",
        )
        addProbes(
            plugin,
            sessionId,
            countAddProbes = countAddProbes,
            sizeExec = sizeExec,
            sizeProbes = sizeProbes
        )
        println("it has added probes, starting finish session...")
        val finishedSession = plugin.state.finishSession(sessionId)
        println("finished session with size peerobes = ${finishedSession?.probes?.size}")
        return finishedSession
    }

    private fun addProbes(
        plugin: Plugin,
        sessionId: String,
        countAddProbes: Int,
        sizeExec: Int,
        sizeProbes: Int,
    ) {
        repeat(countAddProbes) { index ->
            index.takeIf { it % 10 == 0 }?.let { println("adding probes, index = $it...") }
            val execClassData = (0 until sizeExec).map {
                ExecClassData(
                    className = "foo/Bar/$index/$it",
                    probes = randomBoolean(sizeProbes),
                    testId = "$index/$it"
                )
            }
            plugin.sessionHolder.addProbes(sessionId) { execClassData }
        }
    }

    private fun randomBoolean(n: Int = 100) = (0 until n).map { Random.nextBoolean() }.toBitSet()

}
